# Boxing

Paper **26.1.2** plugin for 1v1 arena boxing with entry fees, spectator betting, and live scoreboards.

## Features

- **Arenas** with fighter spawns, lobby, and spectator points
- **Entry fee** charged when joining (Vault economy, or built-in balances if Vault is missing)
- **Betting** on either fighter while the countdown is running
- **Payouts**: winner receives half the pot; the other half is shared by bettors who picked the winner (proportional to stake)
- **Scoreboard** showing fighters, HP, pot, bets, and timer
- **Admin setup** commands for arenas, fees, and force start/stop

## Requirements

- Paper **26.1.2** (Java 25+)
- Optional: [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy plugin (EssentialsX, etc.)

Without Vault, the plugin uses `plugins/Boxing/balances.yml` with a starting balance of **1000**.

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
./gradlew build
```

Jar output: `build/libs/Boxing-1.0.0.jar`

## Quick setup

1. Drop the jar into `plugins/` and restart.
2. Stand where you want each point and run:

```text
/boxingadmin create ring1
/boxingadmin setspawn1 ring1
/boxingadmin setspawn2 ring1
/boxingadmin setlobby ring1
/boxingadmin setspectator ring1
/boxingadmin setfee ring1 100
```

3. Players:

```text
/boxing join ring1
/boxing bet <player|1|2> <amount>
/boxing spectate ring1
/boxing info ring1
```

## Payout math

```text
pot = entry fees + all bets
winner gets pot × winner-share      (default 0.5)
remaining pot goes to winning bettors, split by bet size
if nobody bet on the winner, the winner takes the full pot
draws / force-stops refund fees and bets
```

## Commands

| Command | Description |
|---------|-------------|
| `/boxing join <arena>` | Pay fee and queue |
| `/boxing leave` | Leave queue (refund) |
| `/boxing bet <fighter> <amount> [arena]` | Place or update a bet |
| `/boxing arenas` | List arenas |
| `/boxing info [arena]` | Pot / fighters |
| `/boxing spectate <arena>` | Teleport to spectator spawn |
| `/boxing stats` | Balance |
| `/boxingadmin create\|delete <name>` | Manage arenas |
| `/boxingadmin setspawn1\|setspawn2\|setlobby\|setspectator <arena>` | Set locations |
| `/boxingadmin setfee <arena> <amount>` | Per-arena fee |
| `/boxingadmin forcestart\|forcestop <arena>` | Admin match control |
| `/boxingadmin reload` | Reload config |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `boxing.use` | true | Player commands |
| `boxing.bet` | true | Place bets |
| `boxing.admin` | op | Admin commands |
| `boxing.bypass.fee` | false | Join without paying |

## Config

See `config.yml` for entry fee, countdown, match timeout, winner share, bet limits, kit options, and messages.
