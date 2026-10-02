# MWS: Minecraft Web Services

Run a data center in Minecraft. Server racks need clean 240 V power, they turn every watt
into heat, and they serve real object storage to ComputerCraft. Add UPS batteries for
blackouts and chillers to keep everything cool.

An addon for [Create](https://modrinth.com/mod/create), [Create: Power Grid](https://modrinth.com/mod/create-power-grid)
and [CC: Tweaked](https://modrinth.com/mod/cc-tweaked).

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1+ |
| Create | 6.0.9 – 6.0.x |
| Create: Power Grid | 0.6.2+ |
| CC: Tweaked | 1.120+ |

## Blocks and items

**Server Rack.** Holds up to 4 **Server Blades** (500 W each). Wire it to a 240 V supply.
A rack only boots within 5% of 240 V, and it crashes if the voltage drifts more than 15% while
it runs. An unclean shutdown can lose stored data, and anything over 300 V fries the blades.
- Stack racks into a **cabinet**. The bottom rack has two inlets, Feed A and Feed B, and powers
  the whole stack. If one feed fails, the other carries the load.
- Heat: one blade cools itself, two run throttled, and three or more need a Chiller. Racks
  throttle at 70 °C, shut down at 85 °C, and blades start melting at 110 °C.

**UPS.** Mains goes into the input on the left of the back panel, and racks plug into the output on the
right. It switches to battery the instant the mains fails. Right-click with an empty hand to toggle the
output. A comparator reads its charge.

**Chiller.** Cools every rack within 6 blocks down to 30 °C (4 kW). It evaporates water piped in from any side and
leaves sludge behind. Pipe the sludge out, or it gets dumped on the floor.

**Water Intake.** Takes one water source block per second from connected water up to 16 blocks away.
Pump it out with Create pipes.

Engineer's Goggles show every machine's status, power and temperature. For a rack, they also show why it won't
boot. Hold **W** over any MWS item to watch its Ponder scene.

## ComputerCraft

Every machine is a peripheral. A computer touching any rack in a cabinet gets the whole cabinet.

| Peripheral | Methods |
|---|---|
| `mws_server_rack` | `getStatus` `isOnline` `isRedundant` `getFeeds` `getServers` `getInfo` `getVoltage` `getPower` `getTemperature` `getBladeCount` `put` `get` `delete` `list` `getUsedBytes` `getCapacityBytes` |
| `mws_ups` | `getMode` `isOnBattery` `getCharge` `getEnergy` `getCapacity` `getRuntime` `getInfo` `isOutputEnabled` `setOutputEnabled` |
| `mws_chiller` | `getStatus` `getInfo` `getWater` `getSludge` `getCoolingLoad` |

Each blade adds 64 KiB to the cabinet's object store. Storage calls fail with
`503 Service Unavailable` while no server in the cabinet is online, and `put` fails with
`507 Insufficient Storage` when the store is full.

```lua
local rack = peripheral.find("mws_server_rack")
rack.put("motd", "hello from the cloud")
print(rack.get("motd"), rack.getTemperature())
```

## Configuration

All numbers (voltages, blade power, temperatures, chiller capacity, water use and more) are in
`config/mws-common.toml`. Fan volume is in `config/mws-client.toml`.

## Development

```sh
./gradlew build              # mod jar in build/libs
./gradlew runClient          # dev client
./gradlew runGameTestServer  # end-to-end tests on the real Power Grid solver
```

Ponder scenes live in `client/ponder/MwsPonderScenes.java`. Two helper scripts keep their assets in sync:

- `python3 tools/ponder_schematics.py` regenerates the scene schematics in `assets/mws/ponder/`.
- `python3 tools/ponder_lang.py` rebuilds the scene text in `en_us.json`. Run it after editing any scene text.

## License

All rights reserved.
