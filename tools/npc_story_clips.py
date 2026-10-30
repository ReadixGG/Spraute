"""Clips the story «Тринадцатый удар» needs and the rig's animation file lacks. Only adds: what is in the file stays as it is.

python tools/npc_story_clips.py <input.animation.json> <output.animation.json>
"""
import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import npc_anim_gen as gen
from npc_anim_gen import R, arm, arm_l, clip, merge, stance, torso

known = set(gen.clips)
gen.BEZ.update({
    'point_up': [(0.0, .22, gen.EASE), (1.0, 1.5, gen.EASE)],
    'scared': [(0.0, .12, gen.SNAP), (1.2, 1.6, gen.EASE)],
})

gen.section('=== STORY ===')
RAISED = merge(torso(-3, -6, 0, (-14, 6, 0)), arm((-172, -6, 8), (-6, 0, 0)))
clip('point_up', 1.5, [(.22, RAISED), (1.0, merge(RAISED, arm((-166, -6, 8), (-10, 0, 0))))], peak=.6)
clip('pray_stand', .45, [(.45, merge(torso(2, 0, 0, (14, 0, 0)), arm((-58, -38, 6), (-76, 0, 0)), arm_l((-58, -38, 6), (-76, 0, 0))))], hold=True, peak=.45)
clip('head_down', .5, [(.5, {'Head': R(20)})], hold=True, peak=.5)
clip('head_tease', 1.2, [(.2, {'Head': R(-6, -14, 12)}), (.45, {'Head': R(-4, 14, -12)}), (.7, {'Head': R(-6, -14, 12)}), (.95, {'Head': R(-4, 10, -8)})], peak=.2)
CURLED = merge(stance(2, r_z=-1, l_z=1), torso(10, 0, 0, (12, 0, 0)), arm((-70, -34, 8), (-112, 0, 0)), arm_l((-70, -34, 8), (-112, 0, 0)))
clip('scared', 1.6, [(.12, CURLED), (.6, merge(CURLED, torso(13, 0, 0, (14, 0, 0)))), (1.2, CURLED)], peak=.12)

added = {k: v for k, v in gen.clips.items() if k not in known}


def face(a, base, **over):
    c = copy.deepcopy(a[base])
    for bone, v in over.items():
        c['bones'][bone] = {**c['bones'].get(bone, {}), **v}
    return c


def main(src, out):
    data = json.load(open(src, encoding='utf8'))
    a = data['animations']
    faces = {
        'emo_blind': {'loop': True, 'bones': {'REye': {'scale': [0, 0, 1]}, 'LEye': {'scale': [0, 0, 1]}}},
        'emo_worry': face(a, 'sad', Head={'rotation': [4, 0, 0]}, Brows={'position': [0, .4, 0]}, LBrow={'rotation': [0, 0, -14]}, RBrow={'rotation': [0, 0, 14]}),
        'emo_inspired': face(a, 'happy', Head={'rotation': [-8, 0, 0]}, Brows={'position': [0, .5, 0]}),
        'emo_pain': face(a, 'tired', Head={'rotation': [6, 0, 4]}, Eyes={'scale': [1, .55, 1]}, LBrow={'rotation': [0, 0, -12]}, RBrow={'rotation': [0, 0, 12]}),
        'emo_focus': face(a, 'tired', Eyes={'scale': [1, .8, 1]}, Brows={'position': [0, -.25, 0]}, LBrow={'rotation': [0, 0, 6]}, RBrow={'rotation': [0, 0, -6]}),
        'emo_fury': face(a, 'angry', LEyeBG={'scale': [1, .7, 1]}, REyeBG={'scale': [1, .7, 1]}, LBrow={'rotation': [0, 0, 14]}, RBrow={'rotation': [0, 0, -14]}),
    }
    new = {**added, **faces}
    taken = [k for k in new if k in a]
    if taken:
        raise SystemExit('already in the file: %s' % taken)
    data['animations'] = {**a, **new}
    json.dump(data, open(out, 'w', encoding='utf8', newline='\n'), ensure_ascii=False, indent='\t')
    print('clips added:', [k for k in new if not k.startswith('=')])


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
