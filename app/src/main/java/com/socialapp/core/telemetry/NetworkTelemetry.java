package com.socialapp.core.telemetry;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Response;

/** Measures OkHttp calls, including queueing and interceptor delay. */
public final class NetworkTelemetry implements EventListener.Factory {
    enum Outcome { SUCCESS, FAILURE, CANCELLED }

    private final LongSupplier clock;
    private final PerformanceTelemetry sink;
    private final Map<Call, Measurement> active = Collections.synchronizedMap(new WeakHashMap<>());

    public NetworkTelemetry(LongSupplier clock, PerformanceTelemetry sink) {
        this.clock = clock;
        this.sink = sink;
    }

    @Override
    public EventListener create(Call call) {
        return new Measurement(call.request().tag(LoadContext.class));
    }

    /** Called only when the lab interceptor actually injects delay for this call. */
    public void markSimulated(Call call) {
        Measurement measurement = active.get(call);
        if (measurement != null) measurement.simulated = true;
    }

    private final class Measurement extends EventListener {
        private final UUID request = UUID.randomUUID();
        private final LoadContext load;
        private final Phase dns = new Phase();
        private final Phase connect = new Phase();
        private final Phase tls = new Phase();
        private final Phase requestBody = new Phase();
        private final Phase responseBody = new Phase();
        private long started;
        private Long sent;
        private Long received;
        private Integer httpStatus;
        private volatile boolean simulated;
        private boolean ended;

        Measurement(LoadContext load) { this.load = load; }

        @Override public synchronized void callStart(Call call) {
            started = clock.getAsLong();
            active.put(call, this);
        }

        @Override public synchronized void dnsStart(Call call, String domainName) { dns.start(); }
        @Override public synchronized void dnsEnd(Call call, String domainName, List<InetAddress> addresses) { dns.end(); }

        @Override public synchronized void connectStart(Call call, InetSocketAddress address, Proxy proxy) { connect.start(); }
        @Override public synchronized void connectEnd(Call call, InetSocketAddress address, Proxy proxy, Protocol protocol) { connect.end(); }
        @Override public synchronized void connectFailed(Call call, InetSocketAddress address, Proxy proxy,
                                                         Protocol protocol, IOException error) {
            connect.end();
            tls.end();
        }

        @Override public synchronized void secureConnectStart(Call call) { tls.start(); }
        @Override public synchronized void secureConnectEnd(Call call, Handshake handshake) { tls.end(); }

        @Override public synchronized void requestBodyStart(Call call) { requestBody.start(); }
        @Override public synchronized void requestBodyEnd(Call call, long byteCount) {
            requestBody.end();
            sent = (sent == null ? 0L : sent) + byteCount;
        }

        @Override public synchronized void responseHeadersEnd(Call call, Response response) {
            httpStatus = response.code();
        }

        @Override public synchronized void responseBodyStart(Call call) { responseBody.start(); }
        @Override public synchronized void responseBodyEnd(Call call, long byteCount) {
            responseBody.end();
            received = (received == null ? 0L : received) + byteCount;
        }

        @Override public void callEnd(Call call) { publish(call, false); }
        @Override public void callFailed(Call call, IOException error) { publish(call, true); }

        private synchronized void publish(Call call, boolean ioFailed) {
            if (ended) return;
            ended = true;
            active.remove(call, this);
            long finished = clock.getAsLong();
            Outcome outcome = call.isCanceled() ? Outcome.CANCELLED
                    : ioFailed || (httpStatus != null && (httpStatus < 200 || httpStatus >= 300))
                    ? Outcome.FAILURE : Outcome.SUCCESS;
            sink.network(request, load, simulated, outcome, httpStatus, started, finished,
                    dns.total, connect.total, tls.total, requestBody.total, responseBody.total, sent, received);
        }
    }

    private final class Phase {
        private long started;
        private boolean running;
        private Long total;

        void start() {
            started = clock.getAsLong();
            running = true;
        }

        void end() {
            if (!running) return;
            long elapsed = clock.getAsLong() - started;
            total = (total == null ? 0 : total) + elapsed;
            running = false;
        }
    }
}
