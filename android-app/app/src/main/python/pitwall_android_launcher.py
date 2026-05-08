"""Bootstrap Pitwall Flask bridge on Android (Chaquopy).

Sets ``PITWALL_ANDROID_HOME`` before any ``pitwall`` imports so DuckDB and
registry paths resolve under the app sandbox. Invoked from Kotlin after
``Python.start()`` — see ``PitwallBridge``.
"""


def main(home: str) -> None:
    import os

    os.environ["PITWALL_ANDROID_HOME"] = os.path.abspath(home)
    from pitwall.__main__ import main as bridge_main

    bridge_main()
