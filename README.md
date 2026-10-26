# Oxygen

Симуляция искусственной жизни на клеточном поле. Боты с геномом живут в мире с кислородом, CO₂
и органикой: фотосинтезируют, перерабатывают органику, охотятся, делятся, собираются в
многоклеточные цепочки и колонии с королевами. Рядом могут жить растения по модели biosim, боты с
нейросетью и **мухи, которыми управляет модель всего мозга дрозофилы** (FlyWire, 139 тыс. нейронов,
на видеокарте).

*English version is [below](#english).*

## История

Форк эксперимента «Кислород».

Было сделано автором оригинала:

- 23.02.25 — добавлен углекислый газ; он красного цвета, не проходит через переизбыток органики и сохраняется вместе с миром;
- 27.04.25 — многоклеточные цепочки, новые константы углекислого газа.

Добавлено в форке: замкнутый мир и новый интерфейс, несколько популяций в одном мире и таблица
взаимодействий между ними, растения biosim, нейросеть ботов и растений, журнал баланса энергии,
сохранение мира, сервер в Docker с просмотром через браузер, перезапуск мира с переносом лучших,
мозг мухи.

## Сборка и запуск

Нужна Java 21+ (проверено на JDK 23). Внешних библиотек нет.

```bash
javac -encoding UTF-8 -d out src/sct/*.java src/sct/plants/*.java src/sct/neuro/*.java
java -cp out sct.Main                                   # окно
java -cp out sct.Main --preset small --seed 7           # маленький мир, воспроизводимый
java -cp out sct.Main --set max_age=1000 --set pht_coeff=90 --theme light
```

| Аргумент | Смысл |
|---|---|
| `--preset default\|small\|big` | набор параметров (`Presets.PRESETS`) |
| `--set имя=значение` | любое `public static` поле `Constant`, можно повторять; опечатка — ошибка с подсказкой |
| `--seed N` | зерно: одно и то же число — один и тот же мир |
| `--w`, `--h`, `--bots` | размер мира и число стартовых ботов |
| `--theme dark\|light` | тема (по умолчанию тёмная) |

## Управление

| Клавиша / мышь | Действие |
|---|---|
| `Space` | пауза / продолжить |
| `N` | один шаг на паузе |
| `,` / `.` | меньше / больше шагов за кадр (1…50, max) |
| `M` | живые графики под миром; `1`…`8` — какие показывать, `L` — длина истории |
| ЛКМ по боту | выбрать (предыдущий выбранный отмечен жёлтым — для сравнения геномов) |
| ПКМ | текущий инструмент: Select, Genome, Remove, Make queen, Info -> console |
| `[` / `]` | предыдущий / следующий инструмент |
| `Esc` | сброс инструмента и выделения |
| `H` | справка: что значат режимы (Predators, Kin, Type...), фоны, графики, клавиши |
| колесо, перетаскивание | масштаб и сдвиг |

Кнопки панели: режимы отрисовки ботов и фона, тема, окно генома, сохранение и загрузка мира
(`saved worlds/*.oxw` — все виды, популяции и настройки), выгрузка истории метрик в CSV.

**New world...** — диалог нового мира, все значения уже проставлены:

- *World & species* — размер, зерно, перезапуск, популяции (тело + мозг + численность + цвет) и
  таблица взаимодействий между популяциями: КПД еды, обмен энергии, передача энергии, цепочки,
  «семечко убивает»;
- *Oxygen bots* — параметры `Constant` и галочки действий каждой популяции ботов;
- *Plants (biosim)* — набор biosim, параметры и действия каждой популяции растений;
- *Neuro* — настройки нейросети популяций с мозгом `neuro`;
- *Flies* — мозг мухи: режим, слоты на видеокарте, доля едоков органики, подключение к сервису;
- *Save... / Load...* — настройки в `.properties` (текст, можно править руками), *Defaults* — настройки запуска.

**Again** — новый мир с теми же настройками.

Режим **Kin** — кланы по геному: боты, чьи геномы отличаются не больше чем на
`max_different_genom` генов, — родня (тот же порог, что «свой/чужой» в команде «посмотреть»).
Режим **Clans** — цвет основателя, который чуть сдвигается при каждой мутации.

**Журнал баланса энергии** (`Ledger`): полная энергия мира = энергия клеток + их тела +
органика. Приход — фотосинтез; расход — плата за жизнь и движение, потолок энергии, обрезка
органики. Строка `balance diff` на панели должна оставаться на уровне погрешности (~1e-5).

## Перезапуск и отбор лучших

`--set restart_mode=all|any` — мир сам создаётся заново, когда вымерли все (любая) популяции;
`--set restart_forced=1 --set restart_steps=100000` — и без вымирания, через столько шагов попытки;
`--set elite=1` — заново он заселяется точными клонами лучших за серию (`elite_size`, по умолчанию 100;
оценка по `elite_fields`: lifetime, children, energy, kills, generation). То же — на вкладке *World & species*.
Подробно — [docs/docker-server.md](docs/docker-server.md#перезапуск-при-вымирании-принудительный-перезапуск-и-перенос-лучших).

## Растения (модель biosim)

Пакет `sct.plants` — перенос модели biosim (отдельный проект на Python + numba): ростки, корни, листья,
проводники, трупы, почва. Правила — как в biosim, энергия целочисленная. Органика общая с
ботами Oxygen. Почва растений — слой **минералов** мира (фон **Minerals / soil**): органика
минерализуется в минералы, корни их забирают. Случайный приток минералов Oxygen по умолчанию
работает только в мире без растений (`--set mineral_influx=1|0|-1`).

```bash
java -cp out sct.Main --set starting_bot_count=0 --set starting_plant_count=1500     # только растения
java -cp out sct.Main --set starting_plant_count=1500                                # боты и растения вместе
java -cp out sct.Main --set starting_plant_count=1500 --set plant_preset=original --set plant.leaf_sun_base=40
```

Параметры — поля `PlantConfig` (имена как в `config.py` biosim): `--set plant.<имя>=<значение>`.
Наборы biosim: `--set plant_preset=config|original|noleak|balanced` (раньше, чем `plant.<имя>`).
Мозг: `plant.think_mode=0` (THINK_NEW) или `1` (THINK_OLD). Действия ростка выключаются:
`--set plant_actions_off=eat,move`.

Связь с газами Oxygen — действие ростка **`absorb_co2`**: вытянуть CO₂ из своей клетки и 8 соседних,
получить энергию (`plant.absorb_co2_energy` за единицу CO₂) и выделить кислород. Нет газа — действие
не удаётся, без ошибок. Доступно нейросети и своим алгоритмам; `think_new` — при `plant.todo_mod=9`
(по умолчанию 8 — правила biosim не меняются, и растения кислород **не выделяют**).

Кислород от листьев вместо ботов: `--set plant.leaf_co2_draw=0.0003` — каждый лист на каждом ходу забирает
столько CO₂ из своей клетки и выделяет O₂ (`plant.leaf_o2_per_co2` за единицу), энергии это не дает;
`--set pht_gas=0` — фотосинтез ботов дает энергию без газообмена (CO₂ не нужен, O₂ не выделяется).
Боты по-прежнему дышат: жизнь, шаг, атака и переработка органики тратят O₂, жизнь выделяет CO₂.

## Нейросеть (NeuroBot, NeuroPlant)

Мозг `neuro` подходит и ботам, и растениям: у каждого существа своя сеть, потомок получает копию
с мутацией (нейроэволюция). Входы — датчики тела, выходы — включённые у популяции действия,
направление и параметры. Настройки сети — у популяции (вкладка **Neuro**): скрытые слои,
активация, память, выбор действия (argmax/softmax), мутации, «свой/чужой» по весам, выключенные датчики.

```bash
java -cp out sct.Main --set bot_brain=neuro --set neuro.hidden=24,8                   # нейро-боты
java -cp out sct.Main --set starting_bot_count=0 --set starting_plant_count=1500 --set plant_brain=neuro
```

Бот с геномом видит нейро-бота (и наоборот) командой «посмотреть» как код 8.

## Мозг мухи (FlyWire)

Популяция с мозгом **`fly`** — те же боты Oxygen, но решения за них принимает модель всего мозга
дрозофилы: 138 639 нейронов и 15,1 млн связей из коннектома FlyWire v783. Нейроны копят заряд и
выдают импульсы (LIF), параметры — как в Shiu et al., Nature 2024. Модель считается на видеокарте
отдельным сервисом на Python, мир ждёт его ответа на каждом ходу. Остальные боты и растения живут
как обычно.

**Что нужно:** видеокарта NVIDIA с CUDA, Python 3 с `torch` (сборка с CUDA), `numpy`, `pandas`,
`pyarrow`, `pyyaml`. Данные коннектома (около 140 МБ) скачиваются отдельно — команды в
[flybrain/README.md](flybrain/README.md). Мир запускать из корня репозитория: он сам стартует
`python flybrain/server.py`.

```bash
java -cp out sct.Main --set starting_fly_count=1                                  # одна муха среди ботов
java -cp out sct.Main --set starting_fly_count=100 --set fly_mode=learn --set fly_slots=200
```

- **Как муха видит мир и что делает** — одно место, [flybrain/mapping.yaml](flybrain/mapping.yaml):
  датчик бота → частота импульсов на группу нейронов (детекторы угрозы, вкус, CO₂, запахи…),
  частота группы моторных нейронов → голос за действие бота (ходьба, поворот, бегство, еда, укус).
  Новое действие в `BotAction` или датчик в `BotBody` подключаются там по имени.
- **Режимы:** `fly_mode=single` — мухи не размножаются; `learn` — размножаются, пока есть свободный
  слот из `fly_slots`. На видеокарте считаются только живые мухи; рекомендуемое их число сервис
  печатает при запуске. Чем больше мух, тем медленнее мир: на каждом ходу он ждёт расчёта всех их мозгов.
- **Питание:** при появлении муха получает тип — фотосинтез или органика (`fly_recyclers` — доля
  едоков органики). Мухи дышат: шаг тратит O₂ и выделяет CO₂ (`fly_move_co2`), еда органикой — наоборот.
  Растение впереди муха может укусить и забрать его энергию.
- **3D-визуализация мозга** — в браузере на http://127.0.0.1:5580, пока работает сервис: все нейроны
  на своих местах, вспышки импульсов выбранной мухи, частоты групп привязки и её текущее действие.
- **В консоли** при гибели мухи печатается итог её жизни: сколько ходов, от чего погибла, тип
  питания, какие действия выбирала.
- **Ограничения:** мухи пока не учатся — связи мозга фиксированы, режим `learn` означает только
  размножение. В Docker-сервере мухи не работают: в контейнере нет видеокарты.

Подробно — [flybrain/README.md](flybrain/README.md).

## Действия и свои алгоритмы

Действия тела бота — `BotAction`: `turn, set_dir, random_dir, move, photo, recycle, attack,
give, share, divide, seed, chain_divide, chain_link, queen_call, queen_become, queen_order`.
Любые можно выключить: `--set bot_actions_off=attack,seed`. Выключенное действие ничего не
делает; геном при этом идёт дальше как обычно.

Мозг выбирается параметром `--set bot_brain=<имя>`: `genome` (геном команд, по умолчанию)
или свой алгоритм. Свой алгоритм — класс `implements Brain<Bot>` (пример — `Brains.SimpleBrain`):
читает поля бота и среду (`energy`, `see()`, `oxygen()`, `organics()`, датчики `BotSensors`)
и вызывает действия: `BotAction.PHOTO.run(b, it)`, `BotAction.ATTACK.run(b, it, BotAction.FORWARD)`.
Чтобы он появился в списке — одна строка в `Brains`.

## Сервер (Docker)

Мир без окна на Linux-сервере: симуляция на полной скорости, просмотр через веб (порт 80, только
чтение, кадр рисуется лишь для зрителя), отдельный порт управления — пауза, скорость, новый мир по
настройкам, сохранения. Пульт с рабочего компьютера — `sct.RemoteControl` с тем же диалогом New world.

```bash
cp .env.example .env && docker compose up -d --build     # наружу — туннелем CloudPub (CLOUDPUB_TOKEN в .env), управление: :8081 (только с сервера)
```

Подробно — [docs/docker-server.md](docs/docker-server.md).

## Проверки и замеры

```bash
javac -encoding UTF-8 -d out src/sct/*.java src/sct/plants/*.java src/sct/neuro/*.java test/sct/*.java
java -Djava.awt.headless=true -cp out sct.SelfTest                       # самопроверка, код выхода 0 — всё прошло
java -Djava.awt.headless=true -cp out sct.GoldenTest                     # эталон: правила ботов Oxygen не изменились
java -Djava.awt.headless=true -cp out sct.Bench --steps 1000 --seed 1 --csv metrics.csv
python flybrain/validate.py                                              # мозг мухи: сахар -> MN9 (Shiu 2024) и скорость
python flybrain/probe_io.py                                              # мозг мухи: какие входы будят какие моторные нейроны
```

## Документы

- [docs/docker-server.md](docs/docker-server.md) — сервер, веб-просмотр, управление, перезапуск и перенос лучших;
- [docs/multispecies-plan.md](docs/multispecies-plan.md) — несколько популяций и видов в одном мире;
- [docs/balance-mixed-world.md](docs/balance-mixed-world.md) — подбор баланса общего мира ботов и растений;
- [docs/bot-biography-plan.md](docs/bot-biography-plan.md) — план биографии бота;
- [flybrain/README.md](flybrain/README.md) — мозг мухи: данные, сервис, привязка, визуализация.

---

<a id="english"></a>

# Oxygen (English)

An artificial life simulation on a cellular grid. Genome-driven bots live in a world with oxygen,
CO₂ and organic matter: they photosynthesize, recycle organics, hunt, divide, link into
multicellular chains and form colonies with queens. They can share the world with biosim-model
plants, neural-network bots and **flies controlled by a whole-brain model of Drosophila**
(FlyWire, 139k neurons, on the GPU).

## History

A fork of the "Oxygen" experiment.

Done by the original author:

- 23.02.25 — carbon dioxide: drawn in red, blocked by excess organics, saved with the world;
- 27.04.25 — multicellular chains, new CO₂ constants.

Added in the fork: a wrap-around world and a new UI, several populations in one world with an
interaction table between them, biosim plants, neural networks for bots and plants, an energy
balance ledger, world saving, a Docker server with a browser viewer, world restart carrying over
the best, the fly brain.

## Build and run

Requires Java 21+ (tested on JDK 23). No external libraries.

```bash
javac -encoding UTF-8 -d out src/sct/*.java src/sct/plants/*.java src/sct/neuro/*.java
java -cp out sct.Main                                   # window
java -cp out sct.Main --preset small --seed 7           # small reproducible world
java -cp out sct.Main --set max_age=1000 --set pht_coeff=90 --theme light
```

| Argument | Meaning |
|---|---|
| `--preset default\|small\|big` | parameter set (`Presets.PRESETS`) |
| `--set name=value` | any `public static` field of `Constant`, repeatable; a typo is an error with a hint |
| `--seed N` | seed: the same number gives the same world |
| `--w`, `--h`, `--bots` | world size and number of starting bots |
| `--theme dark\|light` | theme (dark by default) |

## Controls

| Key / mouse | Action |
|---|---|
| `Space` | pause / resume |
| `N` | single step while paused |
| `,` / `.` | fewer / more steps per frame (1…50, max) |
| `M` | live charts under the world; `1`…`8` — which to show, `L` — history length |
| left click on a bot | select it (the previous selection is marked yellow, to compare genomes) |
| right click | current tool: Select, Genome, Remove, Make queen, Info -> console |
| `[` / `]` | previous / next tool |
| `Esc` | reset tool and selection |
| `H` | help: render modes (Predators, Kin, Type...), backgrounds, charts, keys |
| wheel, drag | zoom and pan |

Panel buttons: bot and background render modes, theme, genome window, world save and load
(`saved worlds/*.oxw` — all species, populations and settings), metric history export to CSV.

**New world...** — the new world dialog, all values prefilled:

- *World & species* — size, seed, restart, populations (body + brain + count + colour) and the
  interaction table between populations: eating efficiency, energy exchange, energy transfer,
  chains, "seed kills";
- *Oxygen bots* — `Constant` parameters and action checkboxes of each bot population;
- *Plants (biosim)* — biosim preset, parameters and actions of each plant population;
- *Neuro* — network settings of populations with the `neuro` brain;
- *Flies* — fly brain: mode, GPU slots, share of organic eaters, service connection;
- *Save... / Load...* — settings as `.properties` (plain text, editable), *Defaults* — startup settings.

**Again** — a new world with the same settings.

**Kin** mode — clans by genome: bots whose genomes differ by at most `max_different_genom` genes
are kin (the same threshold as friend/foe in the "look" command). **Clans** mode — the founder's
colour, shifted slightly at every mutation.

**Energy ledger** (`Ledger`): total world energy = cell energy + their bodies + organics. Income is
photosynthesis; spending is the cost of living and moving, the energy cap, organics trimming. The
`balance diff` line on the panel should stay at rounding level (~1e-5).

## Restart and the best

`--set restart_mode=all|any` — the world recreates itself when all (any) populations die out;
`--set restart_forced=1 --set restart_steps=100000` — also without extinction, after that many steps of a try;
`--set elite=1` — it is repopulated with exact clones of the best of the series (`elite_size`,
100 by default; scored by `elite_fields`: lifetime, children, energy, kills, generation). Same on
the *World & species* tab. Details — [docs/docker-server.md](docs/docker-server.md).

## Plants (biosim model)

The `sct.plants` package ports the biosim model (a separate Python + numba project): sprouts,
roots, leaves, conductors, corpses, soil. Rules as in biosim, integer energy. Organics are shared
with Oxygen bots. Plant soil is the world's **minerals** layer (background **Minerals / soil**):
organics mineralize into minerals, roots take them. Random mineral influx is on by default only in
a world without plants (`--set mineral_influx=1|0|-1`).

```bash
java -cp out sct.Main --set starting_bot_count=0 --set starting_plant_count=1500     # plants only
java -cp out sct.Main --set starting_plant_count=1500                                # bots and plants together
java -cp out sct.Main --set starting_plant_count=1500 --set plant_preset=original --set plant.leaf_sun_base=40
```

Parameters are `PlantConfig` fields (names as in biosim `config.py`): `--set plant.<name>=<value>`.
biosim presets: `--set plant_preset=config|original|noleak|balanced` (before `plant.<name>`).
Brain: `plant.think_mode=0` (THINK_NEW) or `1` (THINK_OLD). Sprout actions can be turned off:
`--set plant_actions_off=eat,move`.

Link to Oxygen gases — the sprout action **`absorb_co2`**: draw CO₂ from its cell and 8 neighbours,
gain energy (`plant.absorb_co2_energy` per unit of CO₂) and release oxygen. No gas — the action
fails silently. Available to the neural network and custom algorithms; `think_new` uses it at
`plant.todo_mod=9` (8 by default — biosim rules unchanged, and plants release **no** oxygen).

Oxygen from leaves instead of bots: `--set plant.leaf_co2_draw=0.0003` — every leaf takes that much CO₂ from its
cell each step and releases O₂ (`plant.leaf_o2_per_co2` per unit), with no energy gain;
`--set pht_gas=0` — bot photosynthesis gives energy without gas exchange (no CO₂ needed, no O₂ released).
Bots still breathe: living, moving, attacking and recycling use O₂, living releases CO₂.

## Neural network (NeuroBot, NeuroPlant)

The `neuro` brain fits both bots and plants: every creature has its own network, a child gets a
mutated copy (neuroevolution). Inputs are body sensors, outputs are the population's enabled
actions, direction and parameters. Network settings belong to the population (**Neuro** tab):
hidden layers, activation, memory, action choice (argmax/softmax), mutations, kin by weights,
disabled sensors.

```bash
java -cp out sct.Main --set bot_brain=neuro --set neuro.hidden=24,8                   # neuro bots
java -cp out sct.Main --set starting_bot_count=0 --set starting_plant_count=1500 --set plant_brain=neuro
```

A genome bot sees a neuro bot (and vice versa) with the "look" command as code 8.

## Fly brain (FlyWire)

A population with the **`fly`** brain is ordinary Oxygen bots whose decisions come from a
whole-brain model of Drosophila: 138,639 neurons and 15.1M connections from the FlyWire v783
connectome. Neurons integrate and fire (LIF), parameters as in Shiu et al., Nature 2024. The model
runs on the GPU as a separate Python service; the world waits for its answer every step. Other
bots and plants live as usual.

**Requirements:** an NVIDIA GPU with CUDA, Python 3 with `torch` (CUDA build), `numpy`, `pandas`,
`pyarrow`, `pyyaml`. Connectome data (about 140 MB) is downloaded separately — commands in
[flybrain/README.md](flybrain/README.md). Run the world from the repository root: it starts
`python flybrain/server.py` by itself.

```bash
java -cp out sct.Main --set starting_fly_count=1                                  # one fly among bots
java -cp out sct.Main --set starting_fly_count=100 --set fly_mode=learn --set fly_slots=200
```

- **How a fly senses the world and acts** — one place, [flybrain/mapping.yaml](flybrain/mapping.yaml):
  bot sensor → spike rate into a neuron group (threat detectors, taste, CO₂, odours…), motor group
  rate → a vote for a bot action (walk, turn, escape, eat, bite). A new action in `BotAction` or a
  sensor in `BotBody` is wired there by name.
- **Modes:** `fly_mode=single` — flies do not multiply; `learn` — they multiply while a slot out of
  `fly_slots` is free. Only living flies are computed on the GPU; the service prints the recommended
  number at start. More flies — slower world: every step it waits for all their brains.
- **Diet:** a new fly gets a type — photosynthesis or organics (`fly_recyclers` is the share of
  organic eaters). Flies breathe: a step uses O₂ and exhales CO₂ (`fly_move_co2`), eating organics
  does the opposite. A fly can bite a plant ahead and take its energy.
- **3D brain view** — in the browser at http://127.0.0.1:5580 while the service runs: all neurons
  in place, spikes of the selected fly, rates of the mapped groups and its current action.
- **Console:** when a fly dies, a summary of its life is printed: steps lived, cause of death,
  diet, actions chosen.
- **Limitations:** flies do not learn yet — brain connections are fixed, `learn` mode only means
  reproduction. Flies do not work in the Docker server: the container has no GPU.

Details — [flybrain/README.md](flybrain/README.md) (in Russian).

## Actions and custom algorithms

Bot body actions are `BotAction`: `turn, set_dir, random_dir, move, photo, recycle, attack,
give, share, divide, seed, chain_divide, chain_link, queen_call, queen_become, queen_order`.
Any can be turned off: `--set bot_actions_off=attack,seed`. A disabled action does nothing; the
genome moves on as usual.

The brain is chosen with `--set bot_brain=<name>`: `genome` (command genome, the default) or a
custom algorithm. A custom algorithm is a class `implements Brain<Bot>` (example —
`Brains.SimpleBrain`): it reads bot fields and the environment (`energy`, `see()`, `oxygen()`,
`organics()`, `BotSensors`) and calls actions: `BotAction.PHOTO.run(b, it)`,
`BotAction.ATTACK.run(b, it, BotAction.FORWARD)`. One line in `Brains` adds it to the list.

## Server (Docker)

A windowless world on a Linux server: simulation at full speed, a web viewer (port 80, read-only,
frames drawn only for viewers), a separate control port — pause, speed, a new world from settings,
saves. Remote control from a workstation — `sct.RemoteControl` with the same New world dialog.

```bash
cp .env.example .env && docker compose up -d --build     # public via a CloudPub tunnel (CLOUDPUB_TOKEN in .env), control: :8081 (server only)
```

Details — [docs/docker-server.md](docs/docker-server.md) (in Russian).

## Checks and benchmarks

```bash
javac -encoding UTF-8 -d out src/sct/*.java src/sct/plants/*.java src/sct/neuro/*.java test/sct/*.java
java -Djava.awt.headless=true -cp out sct.SelfTest                       # self-check, exit code 0 — all passed
java -Djava.awt.headless=true -cp out sct.GoldenTest                     # reference: Oxygen bot rules unchanged
java -Djava.awt.headless=true -cp out sct.Bench --steps 1000 --seed 1 --csv metrics.csv
python flybrain/validate.py                                              # fly brain: sugar -> MN9 (Shiu 2024) and speed
python flybrain/probe_io.py                                              # fly brain: which inputs drive which motor neurons
```

## Documents (in Russian)

- [docs/docker-server.md](docs/docker-server.md) — server, web viewer, control, restart and the best;
- [docs/multispecies-plan.md](docs/multispecies-plan.md) — several populations and species in one world;
- [docs/balance-mixed-world.md](docs/balance-mixed-world.md) — balancing a shared world of bots and plants;
- [docs/bot-biography-plan.md](docs/bot-biography-plan.md) — bot biography plan;
- [flybrain/README.md](flybrain/README.md) — fly brain: data, service, mapping, visualization.
