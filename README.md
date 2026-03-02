# MsgPack Benchmark Suite (Private)

Bộ benchmark thực tế cho stack **Kotlin JVM client + Python backend**.

## Thành phần

- `bench_kotlin_python_lua/`
  - Benchmark JSON vs MessagePack trên payload lớn có transform Lua (qua `lupa`).
- `bench_momo_login_payload/`
  - Benchmark JSON vs MessagePack trên payload login mobile (đã redacted dữ liệu nhạy cảm).

## Chạy benchmark

### 1) Kotlin + Python + Lua

```bash
python bench_kotlin_python_lua/runner.py
```

### 2) Payload login

```bash
python bench_momo_login_payload/runner.py
```

> `bench_momo_login_payload/payload.json` đang là bản mẫu redacted. Có thể thay payload thật khi test nội bộ.

## Kết quả gần nhất (tham chiếu)

- Payload login (~1.5KB): MsgPack tiết kiệm ~7.36% size, latency giảm ~16.55%, RPS tăng ~19.91%.

## Ghi chú bảo mật

- Không commit payload thật chứa token/định danh cá nhân.
- Chỉ dùng `payload.sample.json` hoặc dữ liệu đã redacted.
