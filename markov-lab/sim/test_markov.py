from offline_simulation import Markov, load_sessions


def format_res(res):
    return "[" + ", ".join(f"{k}:{v:.3f}" for k, v in res) + "]"


def run_unit_tests():
    print("==========================================================")
    print("   BỘ TEST KIỂM THỬ THUẬT TOÁN MARKOV (PYTHON)")
    print("==========================================================")
    passed, failed = 0, 0

    def check(name, cond, detail):
        nonlocal passed, failed
        if cond:
            passed += 1
            print(f"  [PASS] {name} -> {detail}")
        else:
            failed += 1
            print(f"  [FAIL] {name} -> {detail}")

    # CASE 1: Ví dụ tính tay cơ bản
    pred = Markov(max_order=2, min_support=3)
    ctx = ["A", "B"]
    pred.learn(ctx, "C")
    pred.learn(ctx, "C")
    pred.learn(ctx, "D")
    res = pred.predict(ctx)
    ok = (
        len(res) == 2
        and res[0][0] == "C" and abs(res[0][1] - 2 / 3) < 1e-6
        and res[1][0] == "D" and abs(res[1][1] - 1 / 3) < 1e-6
    )
    check("Case 1 (Tính tay cơ bản [A,B])", ok, format_res(res))

    # CASE 2: Cold-start / Dưới ngưỡng minSupport (< 3)
    pred = Markov(max_order=2, min_support=3)
    ctx = ["Home", "List"]
    pred.learn(ctx, "Detail")
    pred.learn(ctx, "Detail")
    res = pred.predict(ctx)
    check("Case 2 (Dưới ngưỡng minSupport=3 -> Rỗng)", len(res) == 0, f"size={len(res)}")

    # CASE 3: Hạ bậc (Backoff từ Bậc 2 xuống Bậc 1)
    pred = Markov(max_order=2, min_support=3)
    for _ in range(3):
        pred.learn(["Home", "List"], "Detail")
    pred.learn(["Search", "List"], "Cart")
    res = pred.predict(["Search", "List"])
    ok = (
        len(res) == 2
        and res[0][0] == "Detail" and abs(res[0][1] - 0.75) < 1e-6
        and res[1][0] == "Cart" and abs(res[1][1] - 0.25) < 1e-6
    )
    check("Case 3 (Hạ bậc Backoff Bậc 2 -> Bậc 1)", ok, format_res(res))

    # CASE 4: Ưu tiên Bậc 2 khi Bậc 2 đã đủ minSupport >= 3
    pred = Markov(max_order=2, min_support=3)
    for _ in range(4):
        pred.learn(["Home", "List"], "Detail")
    for _ in range(3):
        pred.learn(["Search", "List"], "Cart")
    res = pred.predict(["Search", "List"])
    ok = len(res) == 1 and res[0][0] == "Cart" and abs(res[0][1] - 1.0) < 1e-6
    check("Case 4 (Ưu tiên Bậc 2 khi đủ minSupport)", ok, format_res(res))

    # CASE 5: Xử lý hòa xác suất (Tie-break A -> Z)
    pred = Markov(max_order=2, min_support=3)
    ctx = ["Home", "Detail"]
    pred.learn(ctx, "Reviews")
    pred.learn(ctx, "Cart")
    pred.learn(ctx, "Reviews")
    pred.learn(ctx, "Cart")
    res = pred.predict(ctx)
    ok = len(res) == 2 and res[0][0] == "Cart" and res[1][0] == "Reviews"
    check("Case 5 (Hòa xác suất 50%-50% -> Xếp A-Z)", ok, format_res(res))

    # CASE 6: Lịch sử dài hơn maxOrder & đầu vào rỗng
    pred = Markov(max_order=2, min_support=3)
    pred.learn(["Home", "List", "Detail"], "Cart")
    pred.learn(["Search", "List", "Detail"], "Cart")
    pred.learn(["Profile", "List", "Detail"], "Reviews")
    res = pred.predict(["Home", "Search", "Profile", "List", "Detail"])
    ok = (
        pred.predict([]) == []
        and len(res) == 2
        and res[0][0] == "Cart"
        and abs(res[0][1] - 2 / 3) < 1e-6
    )
    check("Case 6 (Lịch sử dài > maxOrder & Empty-safe)", ok, format_res(res))

    # CASE 7: So sánh độ chính xác giữa các bậc k = 1, 2, 3 trên sessions.csv
    sessions = load_sessions("sessions.csv")
    print("----------------------------------------------------------")
    print("   SO SÁNH ĐA BẬC (k = 1, 2, 3) TRÊN sessions.csv:")
    for k in (1, 2, 3):
        p_k, hits, n = Markov(max_order=k, min_support=3), 0, 0
        for _, seq in sessions:
            hist = []
            for i in range(len(seq) - 1):
                hist.append(seq[i][0])
                nxt = seq[i + 1][0]
                cand = p_k.predict(hist)
                hits += int(bool(cand) and cand[0][0] == nxt)
                n += 1
                p_k.learn(hist, nxt)
        print(f"   - Bậc k = {k}: top1 = {hits/n:.4f} ({hits}/{n})")

    print("----------------------------------------------------------")
    print(f"KẾT QUẢ: {passed} PASSED, {failed} FAILED")
    print("==========================================================")


if __name__ == "__main__":
    run_unit_tests()
