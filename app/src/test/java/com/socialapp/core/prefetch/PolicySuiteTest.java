package com.socialapp.core.prefetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.network.sim.MockNetworkEngine;
import com.socialapp.core.network.sim.NetworkProfile;
import com.socialapp.core.network.sim.PayloadWeight;
import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.policy.P0_NoPrefetchPolicy;
import com.socialapp.core.prefetch.policy.P1_PrefetchAllPolicy;
import com.socialapp.core.prefetch.policy.P2_MostFrequentPolicy;
import com.socialapp.core.prefetch.policy.P3_MarkovOrder1Policy;
import com.socialapp.core.prefetch.policy.P4_MarkovOrder2Policy;
import com.socialapp.core.prefetch.policy.P5_NetworkCostAdaptivePolicy;
import com.socialapp.core.prefetch.policy.PolicyType;
import com.socialapp.core.prefetch.policy.PrefetchPolicyFactory;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionMatrix;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionTensor;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PolicySuiteTest {

    private MarkovTransitionMatrix matrix;
    private MarkovTransitionTensor tensor;
    private MockNetworkEngine networkEngine;

    @Before
    public void setUp() {
        matrix = new MarkovTransitionMatrix();
        tensor = new MarkovTransitionTensor(matrix);
        networkEngine = MockNetworkEngine.getInstance();
        networkEngine.setSimulationEnabled(true);
        networkEngine.setProfile(NetworkProfile.LTE_4G);
        networkEngine.setWeight(PayloadWeight.NORMAL_1_0);
    }

    @Test
    public void testP0_NoPrefetchPolicy_AlwaysEmpty() {
        P0_NoPrefetchPolicy p0 = new P0_NoPrefetchPolicy();
        assertEquals("P0", p0.getPolicyId());
        assertTrue(p0.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList()).isEmpty());
        assertTrue(p0.predictNextScreens(ScreenPayloadConfig.SCREEN_POST_DETAIL, Arrays.asList("home")).isEmpty());
    }

    @Test
    public void testP1_PrefetchAllPolicy_ReturnsAllAdjacentScreens() {
        P1_PrefetchAllPolicy p1 = new P1_PrefetchAllPolicy(matrix);
        assertEquals("P1", p1.getPolicyId());

        List<String> candidates = p1.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList());
        // In default matrix, HOME connects to POST_DETAIL, SEARCH, PROFILE
        assertTrue(candidates.contains(ScreenPayloadConfig.SCREEN_POST_DETAIL));
        assertTrue(candidates.contains(ScreenPayloadConfig.SCREEN_SEARCH));
        assertTrue(candidates.contains(ScreenPayloadConfig.SCREEN_PROFILE));
    }

    @Test
    public void testP2_MostFrequentPolicy_ReturnsTop1Only() {
        P2_MostFrequentPolicy p2 = new P2_MostFrequentPolicy(matrix);
        assertEquals("P2", p2.getPolicyId());

        List<String> candidates = p2.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList());
        assertEquals(1, candidates.size());
        assertEquals(ScreenPayloadConfig.SCREEN_POST_DETAIL, candidates.get(0)); // 82% vs 12% vs 6%
    }

    @Test
    public void testP3_MarkovOrder1Policy_ThresholdFilteringAndOnlineLearning() {
        P3_MarkovOrder1Policy p3 = new P3_MarkovOrder1Policy(matrix, 0.50);
        assertEquals("P3", p3.getPolicyId());

        // Baseline: PostDetail (0.82) >= 0.50, Search (0.12) < 0.50
        List<String> initial = p3.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList());
        assertEquals(1, initial.size());
        assertEquals(ScreenPayloadConfig.SCREEN_POST_DETAIL, initial.get(0));

        // Online Learning: Record 50 transitions from HOME to SEARCH
        for (int i = 0; i < 50; i++) {
            matrix.recordTransition(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_SEARCH);
        }

        // Now SEARCH has 50 transitions out of 50 total recorded -> 100% probability
        List<String> updated = p3.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList());
        assertTrue(updated.contains(ScreenPayloadConfig.SCREEN_SEARCH));
    }

    @Test
    public void testP4_MarkovOrder2Policy_SequenceDisambiguationAndBackoff() {
        P4_MarkovOrder2Policy p4 = new P4_MarkovOrder2Policy(tensor, 0.50);
        assertEquals("P4", p4.getPolicyId());

        // Trajectory A: User comes from HOME -> POST_DETAIL (80% read comments)
        List<String> fromHome = p4.predictNextScreens(
                ScreenPayloadConfig.SCREEN_POST_DETAIL,
                Arrays.asList(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_POST_DETAIL)
        );
        assertEquals(1, fromHome.size());
        assertEquals(ScreenPayloadConfig.SCREEN_COMMENTS, fromHome.get(0));

        // Trajectory B: User comes from SEARCH -> POST_DETAIL (60% check profile)
        List<String> fromSearch = p4.predictNextScreens(
                ScreenPayloadConfig.SCREEN_POST_DETAIL,
                Arrays.asList(ScreenPayloadConfig.SCREEN_SEARCH, ScreenPayloadConfig.SCREEN_POST_DETAIL)
        );
        assertEquals(1, fromSearch.size());
        assertEquals(ScreenPayloadConfig.SCREEN_PROFILE, fromSearch.get(0));

        // Katz Backoff Test: Only 1 step history -> backs off to Order 1
        List<String> backoff = p4.predictNextScreens(
                ScreenPayloadConfig.SCREEN_POST_DETAIL,
                Collections.singletonList(ScreenPayloadConfig.SCREEN_POST_DETAIL)
        );
        assertFalse(backoff.isEmpty());
        assertEquals(ScreenPayloadConfig.SCREEN_COMMENTS, backoff.get(0));
    }

    @Test
    public void testP5_NetworkCostAdaptivePolicy_SuppressesPrefetchOnChokedNetwork() {
        P5_NetworkCostAdaptivePolicy p5 = new P5_NetworkCostAdaptivePolicy(tensor, networkEngine, 1.0);
        assertEquals("P5", p5.getPolicyId());

        // 1. On Wi-Fi: Abundant bandwidth -> Positive utility -> Candidates accepted
        networkEngine.setProfile(NetworkProfile.WIFI);
        List<String> wifiCandidates = p5.predictNextScreens(
                ScreenPayloadConfig.SCREEN_HOME,
                Collections.singletonList(ScreenPayloadConfig.SCREEN_HOME)
        );
        assertFalse("Wi-Fi should prefetch candidates", wifiCandidates.isEmpty());
        assertTrue(wifiCandidates.contains(ScreenPayloadConfig.SCREEN_POST_DETAIL));

        // 2. On EDGE (160 Kbps, CostFactor = 12.0, high penalty lambda = 2.0)
        networkEngine.setProfile(NetworkProfile.EDGE_2G);
        p5.setRiskAversionLambda(2.5); // High risk aversion to conserve tiny bandwidth
        List<String> edgeCandidates = p5.predictNextScreens(
                ScreenPayloadConfig.SCREEN_HOME,
                Collections.singletonList(ScreenPayloadConfig.SCREEN_HOME)
        );
        // On 2G with high penalty, cost outweighs benefit for heavy 250KB payloads!
        assertTrue("EDGE 2G with high lambda should suppress heavy prefetch", edgeCandidates.isEmpty());
    }

    @Test
    public void testPrefetchRuntimeConfig_OneLineSwitchAndScenarioParser() {
        PrefetchRuntimeConfig config = PrefetchRuntimeConfig.getInstance();

        // 1-line policy switch
        config.setPolicy(PolicyType.P3_MARKOV_ORDER_1);
        assertEquals(PolicyType.P3_MARKOV_ORDER_1, config.getActivePolicyType());
        assertEquals("P3", PrefetchManager.getInstance().getPolicy().getPolicyId());

        // 1-line batch scenario string (for Replay runner)
        config.applyScenario("POLICY=P5;NETWORK=3G;WEIGHT=HEAVY;PREFETCH=TRUE;LATE_HIT=FALSE;THETA=0.65;LAMBDA=1.5");
        assertEquals(PolicyType.P5_NETWORK_ADAPTIVE, config.getActivePolicyType());
        assertEquals(NetworkProfile.NETWORK_3G, networkEngine.getCurrentProfile());
        assertEquals(PayloadWeight.HEAVY_2_0, networkEngine.getCurrentWeight());
        assertTrue(config.isPrefetchEnabled());
        assertFalse(config.isLateHitEnabled());
        assertEquals(0.65, config.getMarkovThreshold(), 0.001);
        assertEquals(1.5, config.getRiskAversionLambda(), 0.001);

        // Reset to clean default
        config.setPolicy(PolicyType.P2_MOST_FREQUENT);
        config.setLateHitEnabled(true);
        networkEngine.setProfile(NetworkProfile.LTE_4G);
        networkEngine.setWeight(PayloadWeight.NORMAL_1_0);
    }
}
