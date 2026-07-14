"""Local-only SOCKS5 fixture for ModernSocks device smoke tests.

It accepts no-auth or username/password negotiation, never records credentials,
and returns a fixed HTTP response instead of connecting to the requested host.
"""

import argparse
import socket
import threading


REJECT_AUTHENTICATION = False


def receive_exact(connection: socket.socket, size: int) -> bytes:
    data = b""
    while len(data) < size:
        chunk = connection.recv(size - len(data))
        if not chunk:
            raise ConnectionError("client closed")
        data += chunk
    return data


def handle(connection: socket.socket) -> None:
    try:
        greeting = receive_exact(connection, 2)
        methods = receive_exact(connection, greeting[1])
        method = 2 if 2 in methods else 0
        connection.sendall(bytes((5, method)))
        if method == 2:
            _, username_length = receive_exact(connection, 2)
            receive_exact(connection, username_length)
            password_length = receive_exact(connection, 1)[0]
            receive_exact(connection, password_length)
            connection.sendall(b"\x01\x01" if REJECT_AUTHENTICATION else b"\x01\x00")
            if REJECT_AUTHENTICATION:
                return

        request = receive_exact(connection, 4)
        address_type = request[3]
        if address_type == 1:
            receive_exact(connection, 4)
        elif address_type == 3:
            receive_exact(connection, receive_exact(connection, 1)[0])
        elif address_type == 4:
            receive_exact(connection, 16)
        receive_exact(connection, 2)
        connection.sendall(b"\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x00")
        # The fixture does not proxy TLS, so do not leave non-HTTP browser
        # probes open indefinitely and distort the tunnel shutdown smoke test.
        connection.settimeout(2.0)

        http_request = b""
        while b"\r\n\r\n" not in http_request and len(http_request) < 16_384:
            chunk = connection.recv(4096)
            if not chunk:
                break
            http_request += chunk
        body = b"ModernSocks Phase 4 OK"
        connection.sendall(
            b"HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "
            + str(len(body)).encode("ascii")
            + b"\r\nConnection: close\r\n\r\n"
            + body
        )
    except (ConnectionError, OSError, ValueError, TimeoutError):
        pass
    finally:
        connection.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="ModernSocks SOCKS5 test fixture")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=1080)
    parser.add_argument("--reject-auth", action="store_true")
    args = parser.parse_args()
    global REJECT_AUTHENTICATION
    REJECT_AUTHENTICATION = args.reject_auth
    with socket.create_server((args.host, args.port), reuse_port=False) as server:
        while True:
            connection, _ = server.accept()
            threading.Thread(target=handle, args=(connection,), daemon=True).start()


if __name__ == "__main__":
    main()
