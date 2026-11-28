"""Tests for tools/libsig.py: malformed IEEE-695 input raises ValueError."""

import pytest

import libsig


def test_as_int_accepts_numbers() -> None:
    assert libsig.as_int(42, "size") == 42


def test_as_int_rejects_names() -> None:
    with pytest.raises(ValueError, match="size: expected a number"):
        libsig.as_int("R1", "size")


def test_as_str_accepts_names() -> None:
    assert libsig.as_str("R1", "value") == "R1"


def test_as_str_rejects_numbers() -> None:
    with pytest.raises(ValueError, match="value: expected a name"):
        libsig.as_str(7, "value")


def test_parse_module_rejects_wrong_start_record() -> None:
    with pytest.raises(ValueError, match="module start"):
        libsig.parse_module(b"\xf1\x00", 0)
