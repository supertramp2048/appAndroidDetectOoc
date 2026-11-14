from offline_simulation import Markov, load_sessions

sessions = load_sessions("sessions.csv")
pred, hits, n = Markov(2), 0, 0
for sid, seq in sessions:
    hist = []
    for i in range(len(seq) - 1):
        hist.append(seq[i][0]); nxt = seq[i + 1][0]
        p = pred.predict(hist)
        hits += bool(p) and p[0][0] == nxt; n += 1
        pred.learn(hist, nxt)
print(f"Python top1 = {hits/n:.4f} ({hits}/{n})")
