import numpy as np
import pytest
from app.matting import MattingError, alpha_prob, fuse, tensor, warp

def test_longpipe_input():
    rgb = np.zeros((4, 4, 3), dtype=np.uint8); rgb[:, :, 0] = 255
    x = tensor(rgb, (8, 8))
    assert x.shape == (1, 3, 8, 8) and x[0, 0, 0, 0] == 1

def test_biref_input():
    rgb = np.zeros((4, 4, 3), dtype=np.uint8)
    x = tensor(rgb, (8, 8), biref=True)
    assert np.isclose(x[0, 0, 0, 0], -.485 / .229, atol=.0001)

def test_sigmoid_logits():
    assert np.allclose(alpha_prob(np.zeros((1, 1, 4, 4), dtype=np.float32), logits=True), .5)

def test_zero_optical_flow():
    mask = np.zeros((20, 30), dtype=np.float32); mask[3:7, 10:20] = .75
    flow = np.zeros((1, 2, 6, 10), dtype=np.float32)
    assert np.allclose(warp(mask, flow), mask)

def test_background_not_fused():
    p = np.zeros((80, 120), dtype=np.float32); p[20:70, 40:80] = 1
    fine = np.ones_like(p)
    rgb = np.zeros((80, 120, 3), dtype=np.uint8)
    output = fuse(p, fine, rgb, rgb)
    assert np.all(output[:, :30] == 0) and np.all(output[:, 90:] == 0)

def test_missing_person_fails():
    empty = np.zeros((80, 120), dtype=np.float32)
    with pytest.raises(MattingError):
        fuse(empty, empty, np.zeros((80, 120, 3), dtype=np.uint8), None)
