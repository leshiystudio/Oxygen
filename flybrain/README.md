# Мозг мухи для Oxygen

Модель всего мозга дрозофилы (FlyWire v783: 138 639 нейронов, 15,1 млн связей) управляет ботом
Oxygen. Нейроны копят заряд и выдают импульсы (LIF), параметры как в Shiu et al., Nature 2024.
Считается на видеокарте NVIDIA (PyTorch + CUDA). Остальные боты живут как обычно.

```bash
java -cp out sct.Main --set starting_fly_count=1                                  # одна муха среди ботов
java -cp out sct.Main --set starting_fly_count=4 --set fly_mode=learn --set fly_slots=16
```

Мир сам запускает `python flybrain/server.py` и ждёт ответа мозга на каждом ходу. При запуске
сервис печатает, сколько мух рекомендуется: столько, чтобы мир шёл не медленнее 20 ходов в секунду.
Когда муха гибнет, в консоль выводится её итог: сколько ходов прожила, от чего погибла и какие
действия выбирала.

| Параметр (`--set`) | Смысл |
|---|---|
| `starting_fly_count` | мух при создании мира (популяция Flies); 0 — мух нет |
| `fly_mode` | `single` — мухи не размножаются; `learn` — размножаются, пока есть свободный слот |
| `fly_slots` | слотов на видеокарте в режиме `learn` |
| `fly_port`, `fly_python` | порт сервиса и чем его запускать |

## 3D-визуализация мозга

Пока сервис работает, мозг можно смотреть в браузере: **http://127.0.0.1:5580** (адрес сервис
печатает при запуске; `--viz-port 0` — выключить). На странице:

- все нейроны точками на своих местах, цвет — класс нейрона;
- группы из `mapping.yaml` — своими цветами;
- вспышки — импульсы выбранной мухи за последний ход;
- сбоку — частоты групп и действие, которое выбрала муха.

Муху выбирают в списке, щелчок по группе её прячет. Страница берёт three.js с cdnjs, так что
нужен интернет.

## Привязка — [mapping.yaml](mapping.yaml)

Всё, что связывает муху с ботом, описано в одном файле:

- `groups` — именованные группы нейронов по аннотациям FlyWire (`cell_type`, `cell_class`, `side`...).
- `sensors` — датчик бота → частота импульсов на группы.
- `actions` — частота группы → голос за действие бота. Побеждает самый сильный голос среди
  действий, включённых у популяции.

Датчики и действия называются так же, как у нейро-бота (`sct.neuro.BotBody`). Новое действие в
`sct.BotAction` или новый датчик в `BotBody` сразу можно указать здесь по имени. На опечатку
сервис отвечает списком допустимых имён.

## Файлы

| Файл | Что внутри |
|---|---|
| `brain.py` | LIF-модель: пачка мух, плотный режим (CUDA graph) и событийный |
| `mapping.py` | чтение привязки и шаг «датчики → мозг → действия» |
| `server.py` | TCP-сервис для `sct.neuro.FlyBrain`, протокол — в начале файла |
| `validate.py` | проверка по Shiu (сахар → MN9) и замер скорости |
| `probe_io.py` | таблица «вход → выход»: на что отвечают моторные группы (для настройки привязки) |
| `viz.py`, `viz.html` | 3D-визуализация в браузере |
| `data/` | данные, в git не входят (ниже — откуда их взять) |

```bash
B=https://raw.githubusercontent.com/philshiu/Drosophila_brain_model/main
curl -L -o flybrain/data/Completeness_783.csv $B/Completeness_783.csv
curl -L -o flybrain/data/Connectivity_783.parquet $B/Connectivity_783.parquet
curl -L -o flybrain/data/neuron_annotations.tsv https://raw.githubusercontent.com/flyconnectome/flywire_annotations/main/supplemental_files/Supplemental_file1_neuron_annotations.tsv
```

Данные: FlyWire (Dorkenwald et al., Schlegel et al., Nature 2024), CC BY 4.0; модель — Shiu et al.,
Nature 2024, MIT. В Docker-сервере мухи не работают: в контейнере нет видеокарты.
