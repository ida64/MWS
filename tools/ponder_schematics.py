"""
Generates the Ponder scene schematics in src/main/resources/assets/mws/ponder/.

Each scene is a checkerboard base plate (y = 0) plus the blocks listed below. Run from the
repo root after changing a layout: python3 tools/ponder_schematics.py
The camera starts looking from the north-west, so machines face south (fronts toward the viewer).
"""
import os
import nbt

OUT = 'src/main/resources/assets/mws/ponder'
DATA_VERSION = 3955  # 1.21.1

RACK = 'mws:server_rack'
SOURCE = ('powergrid:creative_voltage_source', {'facing': 'south'})
BLOCK_ENTITIES = {'mws:server_rack', 'mws:ups', 'mws:chiller', 'mws:water_intake', 'powergrid:creative_voltage_source'}


def rack(section):
    return (RACK, {'facing': 'south', 'status': 'off', 'blades': '0', 'section': section})


def machine(name, **props):
    return (name, {'facing': 'south', **props})


def schematic(path, size, blocks):
    """blocks: {(x, y, z): (name, props)}; the base plate is added automatically."""
    sx, sy, sz = size
    all_blocks = {(x, 0, z): ('minecraft:white_concrete' if (x + z) % 2 == 0 else 'minecraft:snow_block', {})
                  for x in range(sx) for z in range(sz)}
    all_blocks.update(blocks)

    palette, index, entries = [], {}, []
    for pos, (name, props) in sorted(all_blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            state = {'Name': (8, name)}
            if props:
                state['Properties'] = (10, {k: (8, v) for k, v in props.items()})
            palette.append(state)
        entry = {'pos': (9, (3, list(pos))), 'state': (3, index[key])}
        if name in BLOCK_ENTITIES:
            entry['nbt'] = (10, {'id': (8, name)})
        entries.append(entry)

    root = {
        'size': (9, (3, list(size))),
        'entities': (9, (10, [])),
        'blocks': (9, (10, entries)),
        'palette': (9, (10, palette)),
        'DataVersion': (3, DATA_VERSION),
    }
    file = os.path.join(OUT, path + '.nbt')
    os.makedirs(os.path.dirname(file), exist_ok=True)
    nbt.write(file, root)
    print('wrote', file)


schematic('server_rack/basics', (5, 3, 5), {
    (2, 1, 2): rack('single'),
    (2, 1, 4): SOURCE,
})

schematic('server_rack/cabinet', (5, 5, 5), {
    (2, 1, 2): rack('bottom'),
    (2, 2, 2): rack('middle'),
    (2, 3, 2): rack('top'),
    (1, 1, 4): SOURCE,
    (3, 1, 4): SOURCE,
})

schematic('ups', (5, 3, 5), {
    (1, 1, 2): machine('mws:ups', mode='off', charge='0'),
    (3, 1, 2): rack('single'),
    (1, 1, 4): SOURCE,
})

schematic('chiller', (5, 3, 5), {
    (1, 1, 2): rack('single'),
    (3, 1, 2): machine('mws:chiller', lit='false'),
    (2, 1, 4): SOURCE,
})

pool = {(x, 1, z): ('minecraft:water', {'level': '0'}) for x in range(3) for z in range(3)}
schematic('water_intake', (5, 3, 5), {
    **pool,
    (3, 1, 1): machine('mws:water_intake', lit='false'),
    (4, 1, 3): SOURCE,
})
