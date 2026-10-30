"""Adds new clips to the NPC rig's animation file, in the same style as its hand-made ones.

python tools/npc_anim_gen.py <input.animation.json> <output.animation.json> [<manifest.json>]

Axes of the rig: an arm raised forward is -X, an elbow bends with -X, a knee with +X, a thigh forward is -X, leaning
or nodding forward is +X. Y+ turns any bone to the character's right, so a right arm goes outward and a torso takes its
right shoulder back. For the right side outward is +Z; the left side mirrors it (x, -y, -z).
Keys are full poses: a bone missing from a key is at rest then.

The .animation.json format has no bezier: Blockbench bakes it into frequent linear keys on export and does not read
handles back. So do the sharp moments here (BEZ): baked curves, which play the same in Blockbench and in the game.
"""
import copy
import json
import math
import sys

THIGH, SHIN = 5.5, 6.5
HIP = 12.0


def R(x=0, y=0, z=0):
    return {'r': [x, y, z]}


def Rp(x=0, y=0, z=0, px=0, py=0, pz=0):
    """pz is forward, where the character looks; in the file forward is -Z."""
    return {'r': [x, y, z], 'p': [px, py, -pz]}


def merge(*poses):
    out = {}
    for p in poses:
        for b, v in p.items():
            out[b] = {**out.get(b, {}), **copy.deepcopy(v)}
    return out


SWAP = (('Right', 'Left'), ('Left', 'Right'))


def side(name):
    for a, b in SWAP:
        if name.startswith(a):
            return b + name[len(a):]
    return name


def mirror(pose):
    out = {}
    for b, v in pose.items():
        n = {}
        if 'r' in v: n['r'] = [v['r'][0], -v['r'][1], -v['r'][2]]
        if 'p' in v: n['p'] = [-v['p'][0], v['p'][1], v['p'][2]]
        if 's' in v: n['s'] = list(v['s'])
        out[side(b)] = n
    return out


def soft(drop):
    """Legs are nearly straight at rest, so a little lowering bends the knees a lot: small drops count for half."""
    return drop * .5 if drop <= 5 else drop


def leg(foot_z=0.0, lift=0.0, drop=0.0, splay=0.0, name='Right'):
    """A leg by where its foot should be: forward, above the ground, with the body lowered by drop."""
    down = HIP - soft(drop) - lift
    d = math.hypot(foot_z, down)
    d = min(d, THIGH + SHIN - 0.01)
    phi = math.degrees(math.atan2(foot_z, down))
    a = math.degrees(math.acos(max(-1, min(1, (THIGH ** 2 + d ** 2 - SHIN ** 2) / (2 * THIGH * d)))))
    inner = math.degrees(math.acos(max(-1, min(1, (THIGH ** 2 + SHIN ** 2 - d ** 2) / (2 * THIGH * SHIN)))))
    z = splay if name == 'Right' else -splay
    return {name + 'Leg': R(-(phi + a), 0, z), name + 'LegKnee': R(180 - inner)}


def stance(drop=0.0, r_z=0.0, l_z=0.0, r_lift=0.0, l_lift=0.0, splay=0.0, npc_z=0.0, npc_x=0.0):
    """Both feet placed; the root lowered to match, so they stay on the ground."""
    return merge({'NPC': Rp(0, 0, 0, npc_x, -soft(drop), npc_z)},
                 leg(r_z, r_lift, drop, splay, 'Right'), leg(l_z, l_lift, drop, splay, 'Left'))


def torso(x=0, y=0, z=0, head=(0, 0, 0)):
    """The bend shared by the two parts of the spine; the head looks on."""
    return {'Body': R(x * .4, y * .4, z * .4), 'BodyKnee': R(x * .6, y * .6, z * .6), 'Head': R(*head)}


def arm(shoulder, elbow=(0, 0, 0), name='Right', shift=None):
    v = {'r': list(shoulder)}
    if shift: v['p'] = list(shift)
    return {name + 'Arm': v, name + 'ArmKnee': R(*elbow)}


def arm_l(shoulder, elbow=(0, 0, 0), shift=None):
    return mirror(arm(shoulder, elbow, 'Right', shift))


PIVOT = 17.0
BACK = 2.0
STEP = 1 / 30


def pin(x, local=(0.0, 0.0), world=(0.0, 0.0)):
    """The root leaned by x, moved so that its point local (height, forward) stays at world: the bone turns about its
    own pivot in the middle of the body, which on its own would swing the feet about."""
    a = math.radians(x)
    dy, dz = local[0] - PIVOT, local[1]
    return Rp(x, 0, 0, 0, world[0] - PIVOT - (dy * math.cos(a) - dz * math.sin(a)), world[1] - (dz * math.cos(a) + dy * math.sin(a)))


def lean(x, foot_y=0.0, foot_z=0.0):
    """The body leaned about the feet; as it comes to lie, on its side of the feet's thickness and not through them."""
    k = math.sin(math.radians(x))
    return {'NPC': pin(x, (foot_y, BACK * k), (0.0, foot_z))}


def bezier(curve, u):
    """A CSS-style cubic-bezier: y for x = u."""
    x1, y1, x2, y2 = curve
    lo, hi = 0.0, 1.0
    for _ in range(30):
        m = (lo + hi) / 2
        if 3 * (1 - m) ** 2 * m * x1 + 3 * (1 - m) * m * m * x2 + m ** 3 < u: lo = m
        else: hi = m
    m = (lo + hi) / 2
    return 3 * (1 - m) ** 2 * m * y1 + 3 * (1 - m) * m * m * y2 + m ** 3


def blend(a, b, k):
    out = {}
    for bone in set(a) | set(b):
        va, vb = a.get(bone, {}), b.get(bone, {})
        out[bone] = {}
        for ch in set(va) | set(vb):
            rest = [1, 1, 1] if ch == 's' else [0, 0, 0]
            out[bone][ch] = [x + (y - x) * k for x, y in zip(va.get(ch, rest), vb.get(ch, rest))]
    return out


def topple(t0, t1, a, b, x0, x1, y0=0.0, y1=0.0, z1=0.0):
    """A fall about the feet: slow while it balances, faster and faster to the ground. Baked, as the feet must stay."""
    n = max(2, round((t1 - t0) / STEP))
    out = []
    for i in range(n + 1):
        u = i / n
        g = u ** 2.3
        out.append((t0 + (t1 - t0) * u, merge(blend(a, b, u), lean(x0 + (x1 - x0) * g, y0 + (y1 - y0) * u, z1 * g))))
    return out


GUARD_R = arm((-38, -18, 12), (-112, 0, 0))
GUARD_L = arm_l((-45, -18, 12), (-118, 0, 0))
FIGHT = merge(stance(2.2, r_z=-2.2, l_z=2.8, splay=5), torso(9, 12, 0, (-6, -10, 0)), GUARD_R, GUARD_L)
SWORD = merge(stance(2.2, r_z=2.6, l_z=-2.4, splay=5), torso(7, -10, 0, (-4, 8, 0)), arm((-52, -14, 16), (-48, 0, 0)), arm_l((-12, 0, 16), (-22, 0, 0)))


# Fast into the blow and landing hard; thrown off at once and easing out; going back without hurry.
STRIKE = (.55, 0, .9, .65)
SNAP = (.08, .75, .2, 1)
EASE = (.3, 0, .2, 1)
BEZ = {
    'punch_right': [(.1, .2, STRIKE), (.3, .45, EASE)],
    'punch_left': [(.0, .14, STRIKE), (.24, .4, EASE)],
    'punch_combo': [(.0, .14, STRIKE), (.24, .4, STRIKE), (.5, .68, STRIKE), (.68, .9, EASE)],
    'hook_right': [(.14, .26, STRIKE), (.36, .55, EASE)],
    'uppercut': [(.16, .3, STRIKE), (.38, .6, EASE)],
    'headbutt': [(.18, .3, STRIKE), (.38, .55, EASE)],
    'kick_front': [(.18, .3, STRIKE), (.4, .65, EASE)],
    'kick_side': [(.24, .38, STRIKE), (.48, .8, EASE)],
    'slash_right': [(.2, .34, STRIKE), (.46, .7, EASE)],
    'slash_left': [(.2, .34, STRIKE), (.46, .7, EASE)],
    'slash_down': [(.32, .46, STRIKE), (.58, .85, EASE)],
    'thrust': [(.18, .3, STRIKE), (.4, .6, EASE)],
    'block_hit': [(.0, .08, SNAP), (.08, .4, EASE)],
    'parry': [(.0, .1, SNAP), (.2, .5, EASE)],
    'dodge_right': [(.0, .16, SNAP), (.28, .5, EASE)],
    'dodge_left': [(.0, .16, SNAP), (.28, .5, EASE)],
    'dodge_back': [(.0, .18, SNAP), (.3, .55, EASE)],
    'hit_front': [(.0, .08, SNAP), (.2, .45, EASE)],
    'hit_back': [(.0, .08, SNAP), (.2, .45, EASE)],
    'hit_right': [(.0, .08, SNAP), (.2, .45, EASE)],
    'hit_left': [(.0, .08, SNAP), (.2, .45, EASE)],
    'throw_item': [(.3, .42, STRIKE), (.42, .8, EASE)],
    'bow_release': [(.0, .06, SNAP), (.26, .5, EASE)],
    'land': [(.0, .1, SNAP), (.32, .6, EASE)],
}
clips = {}
peaks = {}
order = []


def clip(name, length, frames, loop=False, rest_start=True, rest_end=True, hold=False, peak=None, baked=()):
    frames = sorted(frames, key=lambda f: f[0])
    if not loop:
        if rest_start and frames[0][0] > 0: frames = [(0.0, {})] + frames
        if rest_end and not hold and frames[-1][0] < length: frames = frames + [(length, {})]
    elif frames[-1][0] < length:
        frames = frames + [(length, frames[0][1])]
    frames, spans = bake(frames, BEZ.get(name, ()))
    spans += list(baked)
    bones = {}
    for _, p in frames:
        for b, v in p.items():
            for ch in v: bones.setdefault(b, set()).add(ch)
    out = {}
    names = {'r': 'rotation', 'p': 'position', 's': 'scale'}
    for b in bones:
        out[b] = {}
        for ch in sorted(bones[b]):
            track = [(t, p.get(b, {}).get(ch, [1, 1, 1] if ch == 's' else [0, 0, 0]), any(s0 - 1e-6 <= t <= s1 + 1e-6 for s0, s1 in spans)) for t, p in frames]
            track = polish(b, ch, track, length, loop, rest_end and not hold)
            out[b][names[ch]] = {fmt_time(t): {'post': [round(x, 3) for x in v], 'lerp_mode': 'linear' if straight else 'catmullrom'} for t, v, straight in track}
    c = {'animation_length': length, 'bones': out}
    if loop: c['loop'] = True
    if hold: c['loop'] = 'hold_on_last_frame'
    clips[name] = c
    order.append(name)
    # the frame that shows the clip best: the one furthest from rest, unless said otherwise
    if peak is None:
        best, peak = -1, 0.0
        for t, p in frames:
            s = sum(sum(abs(x) for x in v.get('r', [])) + 8 * sum(abs(x) for x in v.get('p', [])) for v in p.values())
            if s > best: best, peak = s, t
    peaks[name] = peak


# What follows lags a little behind what leads it: the torso starts, the head and the forearms come after.
LAG = {'BodyKnee': .012, 'Head': .03, 'RightArmKnee': .03, 'LeftArmKnee': .03, 'RightLegKnee': .016, 'LeftLegKnee': .016}
SETTLERS = ('Head', 'BodyKnee', 'RightArm', 'LeftArm', 'RightArmKnee', 'LeftArmKnee')
SETTLE = .07


def bake(frames, curves):
    """The spans named (from, to, curve) turned into frequent keys along their curve."""
    spans, out = [], list(frames)
    for t0, t1, curve in curves:
        i = next((n for n, f in enumerate(out) if abs(f[0] - t0) < 1e-6), None)
        j = next((n for n, f in enumerate(out) if abs(f[0] - t1) < 1e-6), None)
        if i is None or j != i + 1: raise SystemExit('no such span: %s %s' % (t0, t1))
        n = max(2, round((t1 - t0) / STEP))
        mid = [(t0 + (t1 - t0) * k / n, blend(out[i][1], out[j][1], bezier(curve, k / n))) for k in range(1, n)]
        out[i + 1:i + 1] = mid
        spans.append((t0, t1))
    return out, spans


def polish(bone, channel, track, length, loop, back_to_rest):
    """The overlap of the parts of a body, and a small swing past rest before it comes to stillness."""
    lag = LAG.get(bone, 0) if channel == 'r' else 0
    if lag:
        if loop:
            # moved along and round the loop, it still has to start and end on the same value: the key at the seam
            # is what lies there on the way from the last moved key to the first
            moved = sorted(((t + lag) % length, v, f) for t, v, f in track[:-1])
            t_a, v_a, _ = moved[-1]
            t_b, v_b, _ = moved[0]
            k = (length - t_a) / (length - t_a + t_b)
            seam = [x + (y - x) * k for x, y in zip(v_a, v_b)]
            track = [(0.0, seam, moved[0][2])] + moved + [(length, seam, moved[0][2])]
        elif len(track) > 2:
            mid = [(min(t + lag, length - .01), v, f) for t, v, f in track[1:-1]]
            track = [track[0]] + mid + [track[-1]]
    if back_to_rest and channel == 'r' and bone in SETTLERS and len(track) >= 2 and not any(track[-1][1]) and not track[-1][2]:
        t0, v0, f0 = track[-2]
        t1 = track[-1][0]
        if t1 - t0 >= .2 and any(abs(x) > 3 for x in v0):
            track = track[:-1] + [(t0 + (t1 - t0) * .72, [-x * SETTLE for x in v0], f0), track[-1]]
    return track


def fmt_time(t):
    return ('%.4f' % t).rstrip('0').rstrip('.') if t else '0.0'


def section(title):
    clips[title] = {'loop': True, 'bones': {}}


# ================================================================== fight
section('=== FIGHT ===')
clip('fight_idle', 1.6, [(0, FIGHT), (.8, merge(FIGHT, {'NPC': Rp(0, 0, 0, 0, -2.9, 0)}, stance(2.9, r_z=-2.2, l_z=2.8, splay=5), arm((-34, -18, 12), (-116, 0, 0))))], loop=True)
clip('fight_idle_sword', 1.8, [(0, SWORD), (.9, merge(SWORD, stance(2.8, r_z=2.6, l_z=-2.4, splay=5), arm((-58, -14, 16), (-44, 0, 0))))], loop=True)

jab_r = merge(FIGHT, torso(12, -24, 0, (-8, 8, 0)), arm((-88, -6, 2), (-6, 0, 0)))
clip('punch_right', .45, [(.0, FIGHT), (.1, merge(FIGHT, torso(8, 18, 0, (-6, -10, 0)), arm((-30, -30, 18), (-125, 0, 0)))), (.2, jab_r), (.3, jab_r), (.45, FIGHT)], rest_start=False, rest_end=False, peak=.25)
jab_l = merge(FIGHT, torso(10, 24, 0, (-8, -8, 0)), arm_l((-90, -6, 2), (-6, 0, 0)), stance(2.2, r_z=-2.6, l_z=3.6, splay=5))
clip('punch_left', .4, [(.0, FIGHT), (.14, jab_l), (.24, jab_l), (.4, FIGHT)], rest_start=False, rest_end=False, peak=.19)
clip('punch_combo', .9, [(.0, FIGHT), (.14, jab_l), (.24, FIGHT), (.4, jab_r), (.5, jab_r), (.68, merge(FIGHT, torso(14, 26, 0, (-4, -14, 0)), arm_l((-95, 30, -6), (-70, 0, 0)))), (.9, FIGHT)], rest_start=False, rest_end=False, peak=.45)
hook = merge(FIGHT, torso(10, -38, -6, (-4, 18, 0)), arm((-88, -52, 4), (-78, 0, 0)))
clip('hook_right', .55, [(.0, FIGHT), (.14, merge(FIGHT, torso(6, 26, 4, (-6, -14, 0)), arm((-50, 40, 30), (-92, 0, 0)))), (.26, hook), (.36, hook), (.55, FIGHT)], rest_start=False, rest_end=False, peak=.31)
upper = merge(stance(.8, r_z=-2, l_z=3, splay=5), torso(-16, -20, 0, (-22, 8, 0)), arm((-128, -22, 10), (-96, 0, 0)), GUARD_L)
clip('uppercut', .6, [(.0, FIGHT), (.16, merge(FIGHT, stance(4.2, r_z=-2.2, l_z=2.8, splay=5), torso(18, 10, 0, (-4, 0, 0)), arm((6, -6, 8), (-104, 0, 0)))), (.3, upper), (.38, upper), (.6, FIGHT)], rest_start=False, rest_end=False, peak=.34)
head_bash = merge(FIGHT, torso(30, 0, 0, (22, 0, 0)))
clip('headbutt', .55, [(.0, FIGHT), (.18, merge(FIGHT, torso(-14, 0, 0, (-16, 0, 0)))), (.3, head_bash), (.38, head_bash), (.55, FIGHT)], rest_start=False, rest_end=False, peak=.34)
kick = merge(stance(.4, l_z=-.5, r_z=0, splay=2), {'RightLeg': R(-92, 0, 4), 'RightLegKnee': R(6)}, torso(-20, 0, 0, (16, 0, 0)), arm((-20, 0, 18), (-70, 0, 0)), arm_l((-30, 0, 18), (-80, 0, 0)))
clip('kick_front', .65, [(.0, FIGHT), (.18, merge(kick, {'RightLeg': R(-80, 0, 2), 'RightLegKnee': R(95)})), (.3, kick), (.4, kick), (.65, FIGHT)], rest_start=False, rest_end=False, peak=.35)
round_k = merge(stance(.6, l_z=0, r_z=0), {'RightLeg': R(-40, 20, 76), 'RightLegKnee': R(12)}, {'NPC': Rp(0, -30, 0, 0, -.6, 0)}, torso(4, -14, -30, (0, 24, 18)), arm((-20, 0, 40), (-50, 0, 0)), arm_l((-44, 0, 32), (-92, 0, 0)))
clip('kick_side', .8, [(.0, FIGHT), (.24, merge(round_k, {'RightLeg': R(-60, 10, 40), 'RightLegKnee': R(90), 'NPC': Rp(0, 20, 0, 0, -.6, 0)})), (.38, round_k), (.48, round_k), (.8, FIGHT)], rest_start=False, rest_end=False, peak=.43)

clip('slash_right', .7, [(.0, SWORD), (.2, merge(SWORD, torso(-4, 40, 0, (-6, -22, 0)), arm((-120, 48, 34), (-68, 0, 0)))), (.34, merge(SWORD, stance(3, r_z=4, l_z=-3, splay=5), torso(16, -36, 0, (0, 18, 0)), arm((-72, -64, 6), (-10, 0, 0)))), (.46, merge(SWORD, stance(3, r_z=4, l_z=-3, splay=5), torso(14, -44, 0, (0, 20, 0)), arm((-48, -72, 0), (-26, 0, 0)))), (.7, SWORD)], rest_start=False, rest_end=False, peak=.34)
clip('slash_left', .7, [(.0, SWORD), (.2, merge(SWORD, torso(-2, -40, 0, (-6, 20, 0)), arm((-92, -70, -6), (-74, 0, 0)))), (.34, merge(SWORD, stance(3, r_z=4, l_z=-3, splay=5), torso(14, 34, 0, (0, -18, 0)), arm((-82, 46, 18), (-8, 0, 0)))), (.46, merge(SWORD, stance(3, r_z=4, l_z=-3, splay=5), torso(14, 42, 0, (0, -20, 0)), arm((-60, 56, 24), (-22, 0, 0)))), (.7, SWORD)], rest_start=False, rest_end=False, peak=.34)
over = merge(SWORD, stance(.6, r_z=3, l_z=-3, splay=4), torso(-14, 0, 0, (-14, 0, 0)), arm((-168, -8, 6), (-44, 0, 0)), arm_l((-158, 8, -6), (-52, 0, 0)))
clip('slash_down', .85, [(.0, SWORD), (.32, over), (.46, merge(SWORD, stance(4.4, r_z=4.6, l_z=-4, splay=5), torso(30, 0, 0, (-12, 0, 0)), arm((-46, -8, 2), (-6, 0, 0)), arm_l((-52, 8, -2), (-14, 0, 0)))), (.58, merge(SWORD, stance(4.4, r_z=4.6, l_z=-4, splay=5), torso(32, 0, 0, (-12, 0, 0)), arm((-36, -8, 2), (-6, 0, 0)), arm_l((-42, 8, -2), (-14, 0, 0)))), (.85, SWORD)], rest_start=False, rest_end=False, peak=.32)
thr = merge(stance(3.6, r_z=5, l_z=-4, splay=4), torso(18, -30, 0, (-16, 24, 0)), arm((-92, -6, 0), (-2, 0, 0)), arm_l((10, 0, 28), (-14, 0, 0)))
clip('thrust', .6, [(.0, SWORD), (.18, merge(SWORD, torso(0, 26, 0, (-6, -16, 0)), arm((-50, 26, 24), (-108, 0, 0)))), (.3, thr), (.4, thr), (.6, SWORD)], rest_start=False, rest_end=False, peak=.35)
clip('spin_slash', 1.0, [(.0, SWORD), (.2, merge(SWORD, {'NPC': Rp(0, 80, 0, 0, -2.2, 0)}, arm((-80, 30, 40), (-20, 0, 0)))), (.45, merge(stance(3, r_z=3, l_z=-3, splay=6), {'NPC': Rp(0, 200, 0, 0, -3, 0)}, torso(10, 0, 0), arm((-90, -10, 40), (-6, 0, 0)), arm_l((-30, 0, 40), (-20, 0, 0)))), (.7, merge(stance(3, r_z=3, l_z=-3, splay=6), {'NPC': Rp(0, 320, 0, 0, -3, 0)}, torso(10, 0, 0), arm((-84, -30, 30), (-6, 0, 0)), arm_l((-30, 0, 40), (-20, 0, 0)))), (.85, merge(SWORD, {'NPC': Rp(0, 360, 0, 0, -2.2, 0)})), (1.0, merge(SWORD, {'NPC': Rp(0, 360, 0, 0, -2.2, 0)}))], rest_start=False, rest_end=False, peak=.45)
jump_a = merge(stance(0, r_lift=7, l_lift=4, r_z=-3, l_z=3), {'NPC': Rp(0, 0, 0, 0, 7, 0)}, torso(-14), arm((-170, -8, 4), (-40, 0, 0)), arm_l((-165, 8, -4), (-40, 0, 0)))
land_a = merge(stance(6, r_z=3, l_z=-3, splay=6), torso(34, 0, 0, (-16, 0, 0)), arm((-40, -6, 2), (-6, 0, 0)), arm_l((-46, 6, -2), (-8, 0, 0)))
clip('jump_attack', 1.1, [(.0, SWORD), (.15, merge(SWORD, stance(5, r_z=2, l_z=-2, splay=5), torso(22))), (.42, jump_a), (.6, land_a), (.8, land_a), (1.1, SWORD)], rest_start=False, rest_end=False, peak=.42)

BLOCK = merge(stance(2.4, r_z=-2, l_z=3, splay=5), torso(14, 0, 0, (-4, 0, 0)), arm((-82, -26, 8), (-98, 0, 0)), arm_l((-76, -30, 6), (-110, 0, 0)))
clip('block_hold', 1.2, [(0, BLOCK), (.6, merge(BLOCK, stance(2.9, r_z=-2, l_z=3, splay=5)))], loop=True)
clip('block_hit', .4, [(.0, BLOCK), (.08, merge(BLOCK, lean(-4, 2.4), torso(4, 0, 0, (-10, 0, 0)))), (.4, BLOCK)], rest_start=False, rest_end=False, peak=.08)
parry = merge(SWORD, torso(-2, 24, 0, (-6, -12, 0)), arm((-112, 42, 24), (-52, 0, 0)))
clip('parry', .5, [(.0, SWORD), (.1, parry), (.2, merge(parry, arm((-124, 22, 24), (-40, 0, 0)))), (.5, SWORD)], rest_start=False, rest_end=False, peak=.1)
dodge_r = merge(stance(4.5, r_z=0, l_z=0, splay=14, npc_x=-5), torso(8, 0, -24, (0, 0, 18)), GUARD_R, GUARD_L)
clip('dodge_right', .5, [(.0, FIGHT), (.16, dodge_r), (.28, dodge_r), (.5, FIGHT)], rest_start=False, rest_end=False, peak=.22)
clip('dodge_left', .5, [(.0, FIGHT), (.16, mirror(dodge_r)), (.28, mirror(dodge_r)), (.5, FIGHT)], rest_start=False, rest_end=False, peak=.22)
dodge_b = merge(stance(3.4, r_z=-5, l_z=1, splay=4, npc_z=-5), torso(-22, 0, 0, (14, 0, 0)), GUARD_R, GUARD_L)
clip('dodge_back', .55, [(.0, FIGHT), (.18, dodge_b), (.3, dodge_b), (.55, FIGHT)], rest_start=False, rest_end=False, peak=.24)
tuck = merge(stance(7.8, r_z=2, l_z=2), torso(44, 0, 0, (30, 0, 0)), arm((-60, 0, 8), (-90, 0, 0)), arm_l((-60, 0, 8), (-90, 0, 0)))
clip('roll_forward', .9, [(.1, tuck), (.3, merge(tuck, {'NPC': Rp(120, 0, 0, 0, -9, 7)})), (.5, merge(tuck, {'NPC': Rp(240, 0, 0, 0, -9, 13)})), (.72, merge(tuck, {'NPC': Rp(360, 0, 0, 0, -6, 18)})), (.9, {'NPC': Rp(360, 0, 0, 0, 0, 18)})], rest_end=False, peak=.3)

hit_f = merge(stance(1, r_z=-1, l_z=1), lean(-3, .5), torso(-14, 0, 0, (-20, 0, 0)), arm((14, 0, 20), (-36, 0, 0)), arm_l((14, 0, 20), (-40, 0, 0)))
clip('hit_front', .45, [(.08, hit_f), (.2, hit_f), (.45, {})], peak=.14)
hit_b = merge(stance(1, r_z=1, l_z=-1), lean(4, .5), torso(18, 0, 0, (-18, 0, 0)), arm((26, 0, 14), (-14, 0, 0)), arm_l((26, 0, 14), (-14, 0, 0)))
clip('hit_back', .45, [(.08, hit_b), (.2, hit_b), (.45, {})], peak=.14)
hit_r = merge(torso(4, 22, -18, (-4, 26, -16)), arm((6, 0, 34), (-24, 0, 0)), arm_l((-8, 0, 12), (-28, 0, 0)), stance(1, splay=4))
clip('hit_right', .45, [(.08, hit_r), (.2, hit_r), (.45, {})], peak=.14)
clip('hit_left', .45, [(.08, mirror(hit_r)), (.2, mirror(hit_r)), (.45, {})], peak=.14)
stag_a = merge(stance(2.6, r_z=-5, l_z=1, splay=4, npc_z=-3), torso(-20, 10, 6, (-22, 0, 0)), arm((30, 0, 40), (-30, 0, 0)), arm_l((30, 0, 40), (-34, 0, 0)))
stag_b = merge(stance(3.4, r_z=-1, l_z=-5, splay=4, npc_z=-5), torso(14, -8, -10, (24, 0, 0)), arm((6, 0, 22), (-20, 0, 0)), arm_l((-6, 0, 30), (-24, 0, 0)))
clip('hit_stagger', 1.3, [(.14, stag_a), (.5, stag_b), (.85, merge(stance(1.4, r_z=1, l_z=-1, splay=3, npc_z=-2), torso(8))), (1.3, {})], peak=.14)

back_fall = merge(stance(0), lean(-90), torso(-3, 0, 0, (-6, 0, 12)), arm((6, 0, 34), (-10, 0, 0)), arm_l((4, 0, 44), (-20, 0, 0)), {'RightLegKnee': R(14), 'RightLeg': R(-14, 0, 8)}, {'LeftLegKnee': R(3), 'LeftLeg': R(-2, 0, -8)})
back_rise = merge(back_fall, lean(-85), torso(1, 0, 0, (-12, 0, 8)))
kd_a = merge(hit_f, stance(3, r_z=-3, l_z=1), lean(-7, 1.5), torso(-14, 0, 0, (-20, 0, 0)))
kd_b = merge(back_fall, torso(-8), arm((-30, 0, 44), (-30, 0, 0)), arm_l((-30, 0, 44), (-30, 0, 0)))
clip('knockdown', 1.1, [(.1, kd_a)] + topple(.1, .52, kd_a, back_fall, -7, -90, 1.5, 0) + [(.62, back_rise), (.76, back_fall), (1.1, back_fall)], hold=True, peak=1.1, baked=[(.1, .52)])
gu_a = merge({'RightLeg': R(-100), 'RightLegKnee': R(110), 'LeftLeg': R(-50), 'LeftLegKnee': R(30)}, {'NPC': pin(-55, (12, 0), (BACK, -12))}, torso(30, 0, 0, (20, 0, 0)), arm((-20, 0, 30), (-12, 0, 0)), arm_l((-10, 0, 30), (-8, 0, 0)))
gu_b = merge(stance(9, r_z=8, l_z=6, splay=6, npc_z=-8), torso(34, 0, 0, (-6, 0, 0)), arm((10, 0, 30), (-12, 0, 0)), arm_l((-20, 0, 24), (-30, 0, 0)))
gu_c = merge(stance(7, r_z=1, l_z=-1, splay=6), torso(38, 0, 0, (-14, 0, 0)), arm((-8, 0, 14), (-30, 0, 0)), arm_l((-8, 0, 14), (-30, 0, 0)))
clip('getup', 1.7, [(.0, back_fall), (.35, gu_a), (.72, gu_b), (1.08, gu_c), (1.4, merge(stance(2), torso(10))), (1.7, {})], rest_start=False, rest_end=False, peak=.72)
db_a = merge(stance(1.5), lean(-3, .75), torso(-10, 0, 0, (-24, 0, 0)), arm((10, 0, 24), (-30, 0, 0)), arm_l((-30, 0, 10), (-110, 0, 0)))
db_b = merge(stance(4, r_z=-1, l_z=1), lean(-12, 2), torso(-6, 0, 0, (-20, 0, 0)), arm((-10, 0, 30), (-20, 0, 0)), arm_l((-10, 0, 30), (-60, 0, 0)))
clip('death_back', 1.8, [(.2, db_a), (.6, db_b)] + topple(.6, 1.05, db_b, back_fall, -12, -90, 2, 0) + [(1.15, back_rise), (1.28, back_fall), (1.8, merge(back_fall, torso(0, 0, 0, (-4, 0, 20))))], hold=True, peak=1.8, baked=[(.6, 1.05)])
front_fall = merge({'RightLeg': R(4, 0, 8), 'RightLegKnee': R(10), 'LeftLeg': R(-4, 0, -8), 'LeftLegKnee': R(18)}, lean(90), torso(-3, 0, 0, (-10, 24, 0)), arm((-14, 0, 40), (-12, 0, 0)), arm_l((-158, 0, -30), (-26, 0, 0)))
front_rise = merge(front_fall, lean(86), torso(-6, 0, 0, (-16, 24, 0)))
df_a = merge(stance(2), lean(3, 1), torso(24, 0, 0, (14, 0, 0)), arm((-20, 0, 8), (-60, 0, 0)), arm_l((-20, 0, 8), (-60, 0, 0)))
df_b = merge(stance(5, r_z=2, l_z=-1), lean(12, 2.5), torso(14, 0, 0), arm((-60, 0, 20), (-30, 0, 0)), arm_l((-60, 0, 20), (-30, 0, 0)))
clip('death_front', 1.6, [(.2, df_a), (.6, df_b)] + topple(.6, 1.0, df_b, front_fall, 12, 90, 2.5, 0) + [(1.1, front_rise), (1.22, front_fall), (1.6, front_fall)], hold=True, peak=1.6, baked=[(.6, 1.0)])
KNEE_DOWN = merge({'NPC': Rp(0, 0, 0, 0, -6.2, 0)}, {'RightLeg': R(12, 0, 3), 'RightLegKnee': R(82), 'LeftLeg': R(-78, 0, -3), 'LeftLegKnee': R(82)})
dk_a = merge(KNEE_DOWN, torso(26, 0, 6, (30, 0, 10)), arm((4, 0, 10), (-4, 0, 0)), arm_l((4, 0, 10), (-4, 0, 0)))
dk_end = merge(front_fall, {'NPC': pin(90, (6.2, 0), (BACK, 0))})
dk_fall = [(1.4 + .4 * i / 12, merge(blend(dk_a, dk_end, i / 12), {'NPC': pin(90 * (i / 12) ** 2.3, (6.2, 0), (BACK * math.sin(math.radians(90 * (i / 12) ** 2.3)), 0))})) for i in range(13)]
clip('death_knees', 2.2, [(.3, merge(stance(3), torso(16, 0, 0, (-14, 0, 0)), arm_l((-40, 0, 6), (-90, 0, 0)))), (.8, merge(KNEE_DOWN, torso(18, 0, 0, (-8, 0, 0)), arm((10, 0, 8), (-10, 0, 0)), arm_l((-18, 0, 4), (-60, 0, 0))))] + dk_fall + [(2.2, dk_end)], hold=True, peak=1.4, baked=[(1.4, 1.8)])
clip('victory', 1.8, [(.3, merge(stance(3.5, splay=5), torso(14, 0, 0), arm((-40, 0, 10), (-100, 0, 0)))), (.6, merge(stance(0, splay=4), torso(-12, 0, 0, (-18, 0, 0)), arm((-172, 0, 6), (-12, 0, 0)), arm_l((8, 0, 8), (-30, 0, 0)))), (.9, merge(stance(0, splay=4), torso(-10, 0, 0, (-18, 0, 0)), arm((-164, 0, 6), (-28, 0, 0)), arm_l((8, 0, 8), (-30, 0, 0)))), (1.2, merge(stance(0, splay=4), torso(-12, 0, 0, (-18, 0, 0)), arm((-172, 0, 6), (-12, 0, 0)), arm_l((8, 0, 8), (-30, 0, 0)))), (1.8, {})], peak=.6)
taunt = merge(stance(1, r_z=-1.5, l_z=2, splay=4), torso(-6, 16, 0, (-16, -10, 0)), arm_l((-74, -18, 4), (-10, 0, 0)), arm((6, 0, 18), (-70, 0, 0)))
clip('taunt', 1.6, [(.3, taunt), (.55, merge(taunt, arm_l((-82, -18, 4), (-78, 0, 0)))), (.8, taunt), (1.05, merge(taunt, arm_l((-82, -18, 4), (-78, 0, 0)))), (1.3, taunt), (1.6, {})], peak=.3)
clip('draw_sword', .9, [(.3, merge(torso(4, -18, 0, (6, 10, 0)), arm((-20, -40, -46), (-100, 0, 0)))), (.45, merge(torso(2, -22, 0, (2, 10, 0)), arm((-36, -48, -46), (-110, 0, 0)))), (.68, merge(SWORD, arm((-100, 10, 34), (-20, 0, 0)))), (.9, SWORD)], rest_end=False, peak=.3)
clip('sheath_sword', .9, [(.0, SWORD), (.3, merge(torso(2, -20, 0, (6, 12, 0)), arm((-30, -46, -46), (-108, 0, 0)))), (.5, merge(torso(4, -18, 0, (6, 10, 0)), arm((-14, -36, -46), (-96, 0, 0)))), (.9, {})], rest_start=False, peak=.3)
clip('throw_item', .8, [(.3, merge(stance(1.5, r_z=-4, l_z=3, splay=4), torso(-14, 34, 0, (-6, -22, 0)), arm((-150, 40, 18), (-90, 0, 0)), arm_l((-60, 0, 6), (-10, 0, 0)))), (.42, merge(stance(2.2, r_z=-3, l_z=4, splay=4), torso(22, -28, 0, (-14, 14, 0)), arm((-70, -30, 4), (-6, 0, 0)), arm_l((8, 0, 18), (-24, 0, 0)))), (.8, {})], peak=.3)
BOW = merge(stance(1, r_z=-2, l_z=2.5, splay=5), torso(2, 30, 0, (-2, -30, 0)), arm_l((-90, -24, 0), (-4, 0, 0)), arm((-88, 30, 8), (-118, 0, 0)))
clip('bow_draw', .9, [(.4, merge(BOW, arm((-74, -8, 4), (-90, 0, 0)))), (.9, BOW)], hold=True, peak=.9)
clip('bow_release', .5, [(.0, BOW), (.06, merge(BOW, torso(-2, 34, 0, (-2, -30, 0)), arm((-90, 60, 22), (-58, 0, 0)))), (.26, merge(BOW, arm((-80, 62, 22), (-52, 0, 0)))), (.5, {})], rest_start=False, peak=.06)

# ================================================================== on the move
section('=== MOVE ===')


def steps(left_first=True, wide=2.2, lift=2.8):
    """Stepping on the spot: one foot, then the other."""
    a = merge(stance(.4, r_lift=lift, r_z=wide * .6, l_z=-wide * .3), torso(1, 0, 3))
    b = merge(stance(.1), torso(0))
    c = merge(stance(.4, l_lift=lift, l_z=wide * .6, r_z=-wide * .3), torso(1, 0, -3))
    return (c, b, a, b) if left_first else (a, b, c, b)


for name, first, twist in (('turn_left', True, -8), ('turn_right', False, 8)):
    p = steps(first)
    clip(name, .6, [(0, merge(p[1], {'BodyMain': R(0, twist * .4, 0)})), (.15, merge(p[0], {'BodyMain': R(0, twist, 0)})), (.3, merge(p[1], {'BodyMain': R(0, twist * .4, 0)})), (.45, merge(p[2], {'BodyMain': R(0, -twist * .2, 0)}))], loop=True, peak=.15)


def walk_cycle(length, reach, back_reach, lift, lean, arm_swing, elbow, bob=.45, drop=.3, head=0.0, hip_twist=4.0):
    def legs(rz, rl, lz, ll, d):
        return stance(d, r_z=rz, r_lift=rl, l_z=lz, l_lift=ll)
    contact_r = legs(reach, 0, -back_reach, 0, drop + bob)
    pass_r = legs(0, lift, 0, 0, drop)
    contact_l = legs(-back_reach, 0, reach, 0, drop + bob)
    pass_l = legs(0, 0, 0, lift, drop)
    swing = lambda s: merge(arm((-arm_swing * s, 0, 4), (elbow, 0, 0)), arm_l((arm_swing * s, 0, 4), (elbow, 0, 0)))
    tilt = torso(lean, 0, 0, (head, 0, 0))
    return [(0, merge(contact_r, tilt, swing(-1), {'BodyMain': R(0, hip_twist, 0)})),
            (length * .25, merge(pass_l, tilt, swing(0))),
            (length * .5, merge(contact_l, tilt, swing(1), {'BodyMain': R(0, -hip_twist, 0)})),
            (length * .75, merge(pass_r, tilt, swing(0)))]


clip('fast_walk', .8, walk_cycle(.8, 4.4, 4.0, 4.0, 8, 34, -40, hip_twist=6), loop=True, peak=.0)
clip('walk_back', 1.1, [(t, merge(p, {})) for t, p in walk_cycle(1.1, -3.2, -3.0, 2.8, -4, 12, -24)], loop=True, peak=.0)
strafe = lambda s: [(0, merge(stance(.6, splay=10 * s), torso(0, 0, 0))), (.3, merge(stance(.1, r_lift=2.4), {'NPC': Rp(0, 0, 0, 0, -.1, 0)}, torso(0, 0, 3 * s))), (.6, merge(stance(.6, splay=10 * s), torso(0))), (.9, merge(stance(.1, l_lift=2.4), torso(0, 0, -3 * s)))]
clip('strafe_right', 1.2, strafe(1), loop=True, peak=.0)
clip('strafe_left', 1.2, [(t, mirror(p)) for t, p in strafe(1)], loop=True, peak=.0)
SNK = merge(torso(28, 0, 0, (-24, 0, 0)), arm((-24, 0, 14), (-58, 0, 0)), arm_l((-20, 0, 14), (-62, 0, 0)))
clip('sneak_walk', 1.5, [(0, merge(stance(4, r_z=3.4, l_z=-3.4, splay=3), SNK)), (.375, merge(stance(3.6, l_lift=3), SNK)), (.75, merge(stance(4, l_z=3.4, r_z=-3.4, splay=3), SNK)), (1.125, merge(stance(3.6, r_lift=3), SNK))], loop=True, peak=.0)
clip('tired_walk', 1.6, walk_cycle(1.6, 2.6, 2.2, 1.4, 16, 8, -8, bob=.6, head=14, hip_twist=3), loop=True, peak=.0)
clip('limp', 1.5, [(0, merge(stance(2.6, r_z=2.8, l_z=-2), torso(10, 0, -6), arm_l((-40, 0, 10), (-70, 0, 0)))), (.45, merge(stance(.6, l_lift=1.6, l_z=.6), torso(4, 0, 8), arm_l((-40, 0, 10), (-70, 0, 0)))), (.9, merge(stance(.2, r_z=-2.4, l_z=2.6), torso(2, 0, 10), arm_l((-40, 0, 10), (-70, 0, 0)))), (1.2, merge(stance(1.2, r_lift=2), torso(6, 0, -4), arm_l((-40, 0, 10), (-70, 0, 0))))], loop=True, peak=.0)
clip('jump', .8, [(.14, merge(stance(4.2, splay=4), torso(20), arm((30, 0, 10), (-30, 0, 0)), arm_l((30, 0, 10), (-30, 0, 0)))), (.3, merge(stance(0, r_lift=2, l_lift=2), {'NPC': Rp(0, 0, 0, 0, 3, 0)}, torso(-8, 0, 0, (-10, 0, 0)), arm((-120, 0, 14), (-20, 0, 0)), arm_l((-120, 0, 14), (-20, 0, 0)))), (.8, merge(stance(0, r_lift=3, l_lift=3), {'NPC': Rp(0, 0, 0, 0, 3, 0)}, torso(-4, 0, 0, (-4, 0, 0)), arm((-60, 0, 30), (-30, 0, 0)), arm_l((-60, 0, 30), (-30, 0, 0))))], hold=True, peak=.3)
FALL = merge(stance(0, r_lift=4, l_lift=2, r_z=2, l_z=-2), torso(-4, 0, 0, (10, 0, 0)), arm((-100, 0, 54), (-16, 0, 0)), arm_l((-110, 0, 54), (-20, 0, 0)))
clip('fall', .6, [(0, FALL), (.3, merge(FALL, arm((-110, 0, 60), (-10, 0, 0)), arm_l((-100, 0, 60), (-12, 0, 0))))], loop=True, peak=.0)
clip('land', .6, [(.0, FALL), (.1, merge(stance(6.4, r_z=2, l_z=-2, splay=7), torso(30, 0, 0, (-22, 0, 0)), arm((-14, 0, 26), (-30, 0, 0)), arm_l((-14, 0, 26), (-30, 0, 0)))), (.32, merge(stance(3.4, splay=5), torso(14, 0, 0, (-4, 0, 0)))), (.6, {})], rest_start=False, peak=.1)
clip('climb', 1.2, [(0, merge(stance(0, r_lift=6, r_z=3.4, l_lift=0, l_z=-.6), arm((-160, 0, 8), (-20, 0, 0)), arm_l((-120, 0, 8), (-70, 0, 0)), torso(8, 0, 0, (-24, 0, 0)))), (.6, merge(stance(0, l_lift=6, l_z=3.4, r_lift=0, r_z=-.6), arm((-120, 0, 8), (-70, 0, 0)), arm_l((-160, 0, 8), (-20, 0, 0)), torso(8, 0, 0, (-24, 0, 0))))], loop=True, peak=.0)
PUSH = merge(stance(3.2, r_z=-5.5, l_z=2, splay=2, npc_z=2), torso(32, 0, 0, (-28, 0, 0)), arm((-86, 0, 2), (-24, 0, 0)), arm_l((-86, 0, 2), (-24, 0, 0)))
clip('push', 1.4, [(0, PUSH), (.7, merge(stance(3.2, l_z=-5.5, r_z=2, splay=2, npc_z=2), torso(32, 0, 0, (-28, 0, 0)), arm((-86, 0, 2), (-24, 0, 0)), arm_l((-86, 0, 2), (-24, 0, 0))))], loop=True, peak=.0)
PULL = merge(stance(3.6, r_z=4, l_z=-4, splay=3, npc_z=-3), torso(-20, 0, 0, (12, 0, 0)), arm((-64, 0, 2), (-30, 0, 0)), arm_l((-64, 0, 2), (-30, 0, 0)))
clip('pull', 1.4, [(0, PULL), (.7, merge(stance(4, l_z=4, r_z=-4, splay=3, npc_z=-3), torso(-24, 0, 0, (12, 0, 0)), arm((-60, 0, 2), (-44, 0, 0)), arm_l((-60, 0, 2), (-44, 0, 0))))], loop=True, peak=.0)
CARRY = merge(arm((-46, -6, 6), (-62, 0, 0)), arm_l((-46, -6, 6), (-62, 0, 0)), torso(-6, 0, 0, (6, 0, 0)))
clip('carry_idle', 3.0, [(0, CARRY), (1.5, merge(CARRY, arm((-44, -6, 6), (-64, 0, 0)), arm_l((-44, -6, 6), (-64, 0, 0)), torso(-7, 0, 0, (7, 0, 0))))], loop=True, peak=.0)
clip('carry_walk', 1.1, [(t, merge(p, CARRY)) for t, p in walk_cycle(1.1, 3.2, 2.8, 2.6, 0, 0, 0)], loop=True, peak=.0)

clip('idle_stretch', 3.0, [(.7, merge(stance(0), torso(-16, 0, 0, (-24, 0, 0)), arm((-170, -8, -12), (-20, 0, 0)), arm_l((-170, -8, -12), (-20, 0, 0)))), (1.5, merge(stance(0), torso(-20, 0, 0, (-30, 0, 0)), arm((-176, -8, -14), (-10, 0, 0)), arm_l((-176, -8, -14), (-10, 0, 0)))), (2.2, merge(torso(-6, 0, 0, (-6, 0, 0)), arm((-40, 0, 30), (-90, 0, 0)), arm_l((-40, 0, 30), (-90, 0, 0)))), (3.0, {})], peak=1.5)
clip('idle_look', 4.0, [(.6, torso(0, 6, 0, (0, 34, 0))), (1.4, torso(0, 6, 0, (-4, 40, 0))), (2.2, torso(0, -6, 0, (0, -36, 0))), (3.2, torso(0, -6, 0, (4, -40, 0))), (4.0, {})], peak=1.4)
clip('idle_shift', 3.0, [(.6, merge(stance(.7, r_z=1, l_z=-.4, splay=2), torso(1, 0, 4, (0, 0, -3)), {'BodyMain': Rp(0, 0, 0, 1, 0, 0)})), (1.6, merge(stance(.7, r_z=1, l_z=-.4, splay=2), torso(1, 0, 4, (0, 0, -3)), {'BodyMain': Rp(0, 0, 0, 1, 0, 0)})), (2.2, merge(stance(.4, l_z=.6), torso(0, 0, -2))), (3.0, {})], peak=1.0)
clip('idle_sigh', 2.6, [(.8, merge(torso(-8, 0, 0, (-10, 0, 0)), arm((6, 0, 8)), arm_l((6, 0, 8)))), (1.7, merge(stance(.7), torso(13, 0, 0, (20, 0, 0)), arm((-6, 0, 3)), arm_l((-6, 0, 3)))), (2.6, {})], peak=1.7)
clip('shiver', .24, [(0, merge(torso(4, 0, -1.6, (5, 0, 0)), CARRY, arm((-40, -46, 14), (-98, 0, 0)), arm_l((-40, -46, 14), (-98, 0, 0)))), (.06, merge(torso(4, 0, 1.6, (5, 0, 0)), arm((-41, -46, 15), (-99, 0, 0)), arm_l((-41, -46, 15), (-99, 0, 0)))), (.12, merge(torso(4, 0, -1.6, (5, 0, 0)), arm((-40, -46, 14), (-98, 0, 0)), arm_l((-40, -46, 14), (-98, 0, 0)))), (.18, merge(torso(4, 0, 1.6, (5, 0, 0)), arm((-41, -46, 15), (-99, 0, 0)), arm_l((-41, -46, 15), (-99, 0, 0))))], loop=True, peak=.0)
OUT_OF_BREATH = merge(stance(3, splay=8), torso(28, 0, 0, (-26, 0, 0)), arm((-24, -10, -8), (-46, 0, 0), shift=(0, 0, 0)), arm_l((-24, -10, -8), (-46, 0, 0)))
clip('breath_heavy', 1.2, [(0, OUT_OF_BREATH), (.6, merge(OUT_OF_BREATH, stance(3.7, splay=8), torso(33, 0, 0, (-30, 0, 0))))], loop=True, peak=.0)

# ================================================================== people
section('=== GESTURES ===')
clip('bow', 2.0, [(.5, merge(torso(52, 0, 0, (8, 0, 0)), arm((-8, 0, 4), (-4, 0, 0)), arm_l((-8, 0, 4), (-4, 0, 0)), stance(.3))), (1.2, merge(torso(54, 0, 0, (10, 0, 0)), arm((-8, 0, 4), (-4, 0, 0)), arm_l((-8, 0, 4), (-4, 0, 0)), stance(.3))), (2.0, {})], peak=1.0)
clip('salute', 2.0, [(.4, merge(torso(-3, 0, 0, (-3, 0, 0)), arm((-82, 44, 22), (-122, 0, 0)))), (1.4, merge(torso(-3, 0, 0, (-3, 0, 0)), arm((-82, 44, 22), (-122, 0, 0)))), (2.0, {})], peak=.8)
C1 = merge(arm((-62, -32, 6), (-64, 0, 0)), arm_l((-62, -32, 6), (-64, 0, 0)))
C2 = merge(arm((-60, -14, 12), (-54, 0, 0)), arm_l((-60, -14, 12), (-54, 0, 0)))
clip('clap', .5, [(0, C1), (.25, C2)], loop=True, peak=.25)
clip('cheer', 1.0, [(0, merge(stance(0), torso(-6, 0, 0, (-14, 0, 0)), arm((-168, 0, 22), (-8, 0, 0)), arm_l((-168, 0, 22), (-8, 0, 0)))), (.25, merge(stance(3, splay=4), torso(10, 0, 0, (-4, 0, 0)), arm((-150, 0, 34), (-40, 0, 0)), arm_l((-150, 0, 34), (-40, 0, 0)))), (.5, merge(stance(0), {'NPC': Rp(0, 0, 0, 0, 3.2, 0)}, torso(-8, 0, 0, (-18, 0, 0)), arm((-172, 0, 20), (-4, 0, 0)), arm_l((-172, 0, 20), (-4, 0, 0)))), (.75, merge(stance(3, splay=4), torso(10, 0, 0, (-4, 0, 0)), arm((-150, 0, 34), (-40, 0, 0)), arm_l((-150, 0, 34), (-40, 0, 0))))], loop=True, peak=.5)
clip('facepalm', 2.4, [(.45, merge(torso(4, 0, 0, (18, -8, 0)), arm((-72, -30, 4), (-122, 0, 0)))), (1.7, merge(torso(5, 0, 0, (22, -10, 0)), arm((-76, -30, 4), (-124, 0, 0)))), (2.4, {})], peak=1.0)
THINK = merge(torso(1, 0, 0, (6, 8, -6)), arm((-44, -30, 8), (-132, 0, 0)), arm_l((-20, -24, 6), (-96, 0, 0)))
clip('think', 3.0, [(0, THINK), (1.5, merge(THINK, {'Head': R(2, 14, -8)}))], loop=True, peak=.0)
clip('point', 1.6, [(.3, merge(torso(0, -8, 0, (-3, 6, 0)), arm((-90, -4, 2), (-3, 0, 0)))), (1.1, merge(torso(0, -8, 0, (-3, 6, 0)), arm((-90, -4, 2), (-3, 0, 0)))), (1.6, {})], peak=.7)
BCK = merge(torso(-2), arm((-66, 10, 14), (-20, 0, 0)))
clip('beckon', 1.6, [(.25, BCK), (.5, merge(BCK, arm((-70, 10, 14), (-108, 0, 0)))), (.75, BCK), (1.0, merge(BCK, arm((-70, 10, 14), (-108, 0, 0)))), (1.25, BCK), (1.6, {})], peak=.5)
clip('stop_hand', 1.5, [(.2, merge(torso(-6, 0, 0, (-6, 0, 0)), arm((-92, 0, 8), (-58, 0, 0)))), (1.0, merge(torso(-6, 0, 0, (-6, 0, 0)), arm((-92, 0, 8), (-58, 0, 0)))), (1.5, {})], peak=.6)
clip('refuse', 1.2, [(.2, merge(torso(-8, 12, 0, (-8, -22, 0)), arm((-56, -28, 12), (-50, 0, 0)), arm_l((-56, -28, 12), (-50, 0, 0)))), (.5, merge(torso(-8, 12, 0, (-8, 22, 0)), arm((-60, -18, 16), (-44, 0, 0)), arm_l((-60, -18, 16), (-44, 0, 0)))), (.8, merge(torso(-6, 8, 0, (-6, -14, 0)), arm((-50, -24, 14), (-50, 0, 0)), arm_l((-50, -24, 14), (-50, 0, 0)))), (1.2, {})], peak=.2)
clip('handshake', 1.6, [(.3, merge(torso(4), arm((-48, -8, 2), (-24, 0, 0)))), (.55, merge(torso(4), arm((-52, -8, 2), (-22, 0, 0)))), (.8, merge(torso(4), arm((-40, -8, 2), (-28, 0, 0)))), (1.05, merge(torso(4), arm((-52, -8, 2), (-22, 0, 0)))), (1.6, {})], peak=.55)
HANDS = merge(torso(-4, 0, 0, (-4, 0, 0)), arm((-120, 0, 30), (-90, 0, 0)), arm_l((-120, 0, 30), (-90, 0, 0)))
clip('hands_up', .7, [(.35, merge(HANDS, stance(.6), arm((-130, 0, 30), (-96, 0, 0)), arm_l((-130, 0, 30), (-96, 0, 0)))), (.7, HANDS)], hold=True, peak=.7)
COWER = merge(stance(7, r_z=-1, l_z=1.5, splay=9), torso(40, 0, 0, (14, 0, 0)), arm((-112, -26, 8), (-106, 0, 0)), arm_l((-112, -26, 8), (-110, 0, 0)))
clip('cower', 1.2, [(0, COWER), (.6, merge(COWER, arm((-116, -26, 8), (-108, 0, 0)), arm_l((-108, -26, 8), (-112, 0, 0))))], loop=True, peak=.0)
clip('kneel', 1.1, [(.5, merge(stance(4), torso(10))), (1.1, merge(KNEE_DOWN, torso(6, 0, 0, (10, 0, 0)), arm((6, 0, 6), (-10, 0, 0)), arm_l((-30, 0, 4), (-70, 0, 0))))], hold=True, peak=1.1)
PRAY = merge(KNEE_DOWN, {'RightLeg': R(14, 0, 4), 'LeftLeg': R(14, 0, -4), 'RightLegKnee': R(90), 'LeftLegKnee': R(90), 'NPC': Rp(0, 0, 0, 0, -7, 0)}, torso(6, 0, 0, (20, 0, 0)), arm((-58, -38, 6), (-76, 0, 0)), arm_l((-58, -38, 6), (-76, 0, 0)))
clip('pray', 4.0, [(0, PRAY), (2.0, merge(PRAY, torso(8, 0, 0, (24, 0, 0))))], loop=True, peak=.0)
CRY = merge(stance(.6), torso(16, 0, 0, (28, 0, 0)), arm((-68, -30, 4), (-124, 0, 0)), arm_l((-68, -30, 4), (-124, 0, 0)))
clip('cry', 1.2, [(0, CRY), (.3, merge(CRY, torso(20, 0, 0, (24, 0, 0)))), (.6, CRY), (.9, merge(CRY, torso(13, 0, 0, (32, 0, 0))))], loop=True, peak=.0)
clip('sit_floor', 3.0, [(0, merge({'NPC': Rp(0, 0, 0, 0, -10.8, 0)}, {'RightLeg': R(-76, 18, 6), 'RightLegKnee': R(74), 'LeftLeg': R(-60, -14, -6), 'LeftLegKnee': R(28)}, torso(-10, 0, 0, (2, 0, 0)), arm((14, 0, 12), (-8, 0, 0)), arm_l((-46, 0, 8), (-58, 0, 0)))), (1.5, merge({'NPC': Rp(0, 0, 0, 0, -10.8, 0)}, {'RightLeg': R(-76, 18, 6), 'RightLegKnee': R(74), 'LeftLeg': R(-60, -14, -6), 'LeftLegKnee': R(28)}, torso(-12, 0, 0, (0, 0, 0)), arm((14, 0, 12), (-8, 0, 0)), arm_l((-46, 0, 8), (-58, 0, 0))))], loop=True, peak=.0)
LIE = merge({'NPC': Rp(-90, 0, 0, 0, -15, -8)}, {'RightLeg': R(-3, 0, 4), 'LeftLeg': R(-3, 0, -4)}, arm((-6, 0, 12), (-4, 0, 0)), arm_l((-6, 0, 12), (-4, 0, 0)))
clip('sleep', 4.0, [(0, merge(LIE, torso(0, 0, 0, (0, 0, 14)))), (2.0, merge(LIE, torso(1, 0, 0, (0, 0, 14)), arm((-8, 0, 12), (-4, 0, 0)), arm_l((-8, 0, 12), (-4, 0, 0))))], loop=True, peak=.0)
KNOCK = merge(torso(4), arm((-70, -6, 8), (-92, 0, 0)))
clip('knock', 1.3, [(.25, KNOCK), (.38, merge(KNOCK, arm((-76, -6, 8), (-56, 0, 0)))), (.5, KNOCK), (.62, merge(KNOCK, arm((-76, -6, 8), (-56, 0, 0)))), (.75, KNOCK), (.87, merge(KNOCK, arm((-76, -6, 8), (-56, 0, 0)))), (1.3, {})], peak=.25)
READ = merge(torso(2, 0, 0, (20, 0, 0)), arm((-30, -24, 4), (-80, 0, 0)), arm_l((-30, -24, 4), (-80, 0, 0)))
clip('read', 4.0, [(0, READ), (1.3, merge(READ, {'Head': R(22, 6, 0)})), (2.6, merge(READ, {'Head': R(18, -6, 0)}))], loop=True, peak=.0)
clip('drink', 2.4, [(.5, merge(torso(-2, 0, 0, (-6, 0, 0)), arm((-46, -24, 6), (-112, 0, 0)))), (.9, merge(torso(-6, 0, 0, (-24, 0, 0)), arm((-82, -26, 6), (-112, 0, 0)))), (1.7, merge(torso(-7, 0, 0, (-28, 0, 0)), arm((-90, -26, 6), (-112, 0, 0)))), (2.4, {})], peak=1.3)
EAT = merge(arm((-48, -28, 6), (-124, 0, 0)), torso(2, 0, 0, (6, 0, 0)))
clip('eat', 1.8, [(.3, EAT), (.5, merge(EAT, {'Head': R(14, 0, 0)})), (.7, EAT), (.9, merge(EAT, {'Head': R(14, 0, 0)})), (1.1, EAT), (1.3, merge(EAT, {'Head': R(14, 0, 0)})), (1.8, {})], peak=.5)
clip('pickup', 1.3, [(.5, merge(stance(7.6, r_z=2, l_z=-1.4, splay=5), torso(46, 0, 0, (-16, 0, 0)), arm((-60, -6, 4), (-12, 0, 0)))), (.7, merge(stance(7.6, r_z=2, l_z=-1.4, splay=5), torso(46, 0, 0, (-16, 0, 0)), arm((-54, -6, 4), (-24, 0, 0)))), (1.3, {})], peak=.6)
clip('give', 1.5, [(.5, merge(torso(8, 0, 0, (2, 0, 0)), stance(.6, r_z=1.4, l_z=-.6), arm((-72, -4, 4), (-14, 0, 0)))), (1.0, merge(torso(8, 0, 0, (2, 0, 0)), stance(.6, r_z=1.4, l_z=-.6), arm((-70, -4, 4), (-14, 0, 0)))), (1.5, {})], peak=.7)
clip('look_far', 3.0, [(.5, merge(torso(-4, 0, 0, (-6, 6, 0)), arm((-112, -30, 6), (-110, 0, 0)))), (1.4, merge(torso(-4, 0, 0, (-6, 26, 0)), arm((-112, -30, 6), (-110, 0, 0)))), (2.3, merge(torso(-4, 0, 0, (-6, -16, 0)), arm((-112, -30, 6), (-110, 0, 0)))), (3.0, {})], peak=.5)
clip('dust_off', 1.6, [(.3, merge(torso(12, 0, 0, (16, 0, 0)), arm((-30, -30, -2), (-50, 0, 0)))), (.5, merge(torso(14, 0, 0, (16, 0, 0)), arm((-10, -30, -2), (-30, 0, 0)))), (.7, merge(torso(12, 0, 0, (16, 0, 0)), arm((-30, -30, -2), (-50, 0, 0)))), (.9, merge(torso(14, 0, 0, (16, 0, 0)), arm((-10, -30, -2), (-30, 0, 0)))), (1.6, {})], peak=.3)


# ================================================================== faces
def key_value(k):
    return k if isinstance(k, list) else k.get('post', k.get('pre'))


def value_at(keys, times, t):
    """The value at t by straight lines, held past both ends."""
    if t <= times[0]: return key_value(keys[fmt_key(times, 0)])
    for i in range(1, len(times)):
        if t <= times[i]:
            k = (t - times[i - 1]) / (times[i] - times[i - 1])
            return [x + (y - x) * k for x, y in zip(key_value(keys[fmt_key(times, i - 1)]), key_value(keys[fmt_key(times, i)]))]
    return key_value(keys[fmt_key(times, -1)])


def fmt_key(times, i):
    return times.key_names[i]


class Times(list):
    pass


def close_loops(animations):
    """A looped clip whose track is elsewhere at its end than at its start jumps at the seam. Tracks of the hand-made
    clips are often shifted for overlap, with keys left past the end, where they never play: such a track gets the
    value it passes the seam with, on both sides of it."""
    closed = {}
    for name, c in animations.items():
        length = c.get('animation_length')
        if c.get('loop') is not True or not length: continue
        for bone, channels in c.get('bones', {}).items():
            for channel, keys in channels.items():
                if not isinstance(keys, dict) or len(keys) < 2: continue
                names = sorted(keys, key=float)
                times = Times(float(n) for n in names)
                times.key_names = names
                start, end = value_at(keys, times, 0.0), value_at(keys, times, length)
                jump = max(abs(x - y) for x, y in zip(start, end))
                if jump < .05: continue
                has_start = times[0] < 1e-4
                if has_start:
                    seam = key_value(keys[names[0]])
                else:
                    before = [t for t in times if t <= length + 1e-4][-1]
                    v_b = value_at(keys, times, before)
                    after = next((t for t in times if t > length + 1e-4), None)
                    t_a, v_a = (after, value_at(keys, times, after)) if after else (times[0] + length, key_value(keys[names[0]]))
                    k = (length - before) / (t_a - before) if t_a > before else 0
                    seam = [round(x + (y - x) * k, 4) for x, y in zip(v_b, v_a)]
                template = keys[names[0]]
                for n in names:
                    if float(n) > length + 1e-4: del keys[n]
                def entry():
                    e = copy.deepcopy(template)
                    if isinstance(e, list): return list(seam)
                    e.pop('pre', None)
                    e['post'] = list(seam)
                    return e
                for n in list(keys):
                    if abs(float(n) - length) < 1e-4: del keys[n]
                keys[fmt_time(length)] = entry()
                if not has_start: keys['0.0'] = entry()
                closed.setdefault(name, []).append('%s.%s %.2f' % (bone, channel, jump))
    return closed


def main(argv):
    src, out = argv[1], argv[2]
    data = json.load(open(src, encoding='utf8'))
    a = data['animations']
    for k, v in clips.items():
        if k in a and not k.startswith('='): raise SystemExit('name taken: ' + k)
    new = {}
    new.update(clips)

    # the blink the rig has no clip for: the whites and what is on them close
    blink = {'LEyeBG': {'scale': {'0.0': {'post': [1, 1, 1], 'lerp_mode': 'catmullrom'}, '0.06': {'post': [1, .05, 1], 'lerp_mode': 'catmullrom'}, '0.1': {'post': [1, .05, 1], 'lerp_mode': 'catmullrom'}, '0.17': {'post': [1, 1, 1], 'lerp_mode': 'catmullrom'}}}}
    blink['REyeBG'] = copy.deepcopy(blink['LEyeBG'])
    new['=== FACE (NEW) ==='] = {'loop': True, 'bones': {}}
    new['blink'] = {'animation_length': .17, 'bones': blink}
    order.append('blink')
    peaks['blink'] = .08

    def face(name, base, **over):
        c = copy.deepcopy(a[base])
        for bone, v in over.items():
            c['bones'][bone] = {**c['bones'].get(bone, {}), **v}
        new[name] = c
        order.append(name)
        peaks[name] = 0.0

    face('emo_mild_anger', 'angry', LEyeBG={'scale': [1, .97, 1]}, REyeBG={'scale': [1, .97, 1]}, LBrow={'rotation': [0, 0, 4]}, RBrow={'rotation': [0, 0, -4]})
    face('emo_very_sad', 'sad', Head={'rotation': [9, 0, 0]}, Brows={'position': [0, .2, 0]})
    face('emo_giggle', 'laugh', Eyes={'scale': [1, .55, 1]}, Head={'rotation': [-3, 0, 2]})
    face('emo_suspicion', 'hint', LEyeBG={'scale': [1, .7, 1]}, REyeBG={'scale': [1, .95, 1]})
    face('emo_shame', 'tired', Head={'rotation': [12, -6, 0]}, REye={'position': [.25, -.2, 0]}, LEye={'position': [.25, -.2, 0]})
    face('emo_fear', 'gasp', LBrow={'rotation': [0, 0, -12], 'position': [0, .6, 0]}, RBrow={'rotation': [0, 0, 12], 'position': [0, .6, 0]}, REye={'scale': [.8, .8, 1]}, LEye={'scale': [.8, .8, 1]})
    face('emo_surprise', 'gasp', LBrow={'position': [0, .8, 0]}, RBrow={'position': [0, .8, 0]})
    face('emo_curious', 'question', Head={'rotation': [-2, 6, 8]})
    face('emo_thought', 'tired', Head={'rotation': [-8, 12, 0]}, REye={'position': [-.3, .3, 0]}, LEye={'position': [-.3, .3, 0]})
    face('emo_realize', 'gasp', Head={'rotation': [-8, 0, 0]}, LBrow={'position': [0, .6, 0]}, RBrow={'position': [0, .6, 0]})
    face('emo_resentment', 'disgust', LEyeBG={'scale': [1, .75, 1]}, REyeBG={'scale': [1, .75, 1]}, Head={'rotation': [4, -14, 0]})
    face('emo_offended', 'outrage', Head={'rotation': [-6, 18, 0]})
    face('emo_pride', 'happy', Head={'rotation': [-12, 0, 0]}, Eyes={'position': [0, .1, 0], 'scale': [1, .8, 1]})
    face('emo_playful', 'hint', Head={'rotation': [0, 8, 8]})
    face('emo_cute', 'happy', Head={'rotation': [0, 0, 10]}, Eyes={'scale': [1, .7, 1], 'position': [0, .15, 0]})
    face('emo_mad', 'CrazyPose')

    closed = close_loops(a)
    for name, tracks in closed.items(): print('loop closed:', name, tracks)
    out_data = dict(data)
    out_data['animations'] = {**a, **new}
    with open(out, 'w', encoding='utf8', newline='\n') as f:
        json.dump(out_data, f, ensure_ascii=False, indent='\t')
    if len(argv) > 3:
        json.dump({'order': order, 'peaks': peaks}, open(argv[3], 'w', encoding='utf8'), ensure_ascii=False)
    print('clips added:', len(order))


if __name__ == '__main__':
    main(sys.argv)
