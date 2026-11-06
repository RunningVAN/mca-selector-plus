# MCA Selector Plus

基于 [MCA Selector](https://github.com/Querz/mcaselector) 2.9 的增强分支：复用 MCA Selector 的区块/区域读写、渲染、选区与批量任务核心，在其上实现了一套 **NBT Studio 风格的 NBT 查找与替换** 能力，可直接对整个世界做 NBT 级别的批量搜索、批量改名、批量改值，范围同时覆盖区块文件（region/poi/entities）与世界数据文件（`level.dat`、`data/`、`playerdata/` 及所有维度的同名目录）。

原有功能（地图渲染、选区、导入导出、删除、裁剪、缓存图片等）全部保留。

## 一、能力对照

| NBT Studio 的能力 | MCA Selector Plus 的实现 |
| --- | --- |
| 按标签 Name 查找 | `name=<模式>`，支持大小写不敏感 |
| 按标签 Value 查找 | `value=<模式>` |
| 普通文本 / 精确 / 通配符 | `mode=smart`（默认）/ `mode=contains` / `mode=exact` / `mode=glob` |
| 正则查找 | `mode=regex` |
| 正则替换 + 捕获组 | `mode=regex`，替换串支持 `$1`、`$2`… |
| 只在某个子树/列表元素里找 | `path=block_entities.Items[*].id` |
| 限定作用域（区块 / POI / 实体） | `root=region,poi,entities` |
| 限定标签类型 | `type=string,int,numeric,array,…` |
| 搜索结果列表 / 定位 | 命中的区块变成地图选区，并在同一窗口下半部的「区块」页里逐条列出，双击即打开该区块的编辑器 |
| 操作任意 NBT 文件（`level.dat`、`dat` 等） | 勾选「同时处理世界数据文件」，同一份查询与替换串会压在整个档案的标签树上执行，不只是区块 |
| 不搜索也能直接打开任意文件 | 菜单「工具 → 浏览 NBT 文件…」列出世界里全部可编辑文件（`level.dat`、`data/`、`playerdata/`、各维度同名目录、结构文件），按目录分组，双击任一文件即进编辑器；查找结果仍然只是「按条件筛出来的那部分」，两条入口并存 |
| 只改 Name / 只改 Value / 两者都改 | 替换串前缀 `@name` / `@value` / `@both`，不写则按查询自动判断 |
| 在标签树上直接增删改（不用写查询串） | 区块编辑器与数据文件编辑器共用同一条操作条：红色垃圾桶删除当前选中，12 种 `Tag.Type` 一键新增（`BYTE` / `SHORT` / `INT` / `LONG` / `FLOAT` / `DOUBLE` / `String` / `List` / `Compound` / `[B]` / `[I]` / `[L]`），类型按钮随所选父节点动态可用；区块编辑器根节点可删，数据文件编辑器根节点不可删 |

搜索匹配以 **区块** 为判定单位：一个区块内任意标签命中即算命中。替换则逐个标签精确应用。世界数据文件没有区块概念，以 **文件** 为判定单位，一个文件内任意标签命中即算命中。

## 二、查询语法

查询串由若干 `键=值` 组成，键值对之间用空格分隔；含空格的值用单引号或双引号包起来。

| 键 | 别名 | 说明 | 可选值 |
| --- | --- | --- | --- |
| `name` | `n` | 标签名模式 | 任意文本 / 通配符 / 正则 |
| `value` | `v` | 标签值模式 | 任意文本 / 通配符 / 正则 |
| `path` | `p` | 限定子树路径，用 `.` 连接，列表元素用 `[*]` | 如 `sections[*].palette` |
| `mode` | `m` | 匹配模式 | `smart`(默认) / `contains` / `exact` / `glob` / `regex` |
| `case` | `c` | 大小写 | `sensitive`(默认) / `insensitive` |
| `root` | `r` | 搜索范围 | `region`(默认) / `poi` / `entities` / `all`，可组合 |
| `type` | `t` | 标签类型过滤 | `string` `int` `long` `short` `byte` `float` `double` `list` `compound` `array` `numeric`，可组合 |
| `limit` | `l` | 单个区块最多返回的命中数（性能保护） | 默认 5000 |
| `chunk` | — | 名和值可以落在不同标签上，只要在同一区块里找到就算命中 | `1` / `0`(默认关，GUI 里默认开) |

`name` 与 `value` 至少要写一个；两者都不写、只写一个裸模式串（如 `diamond`）时，等价于同时对 `name` 与 `value` 做匹配。

`smart` 模式的规则：不含通配符时按「包含」处理（等价 `contains`）；含 `*` 或 `?` 时按 glob 处理。因此 `minecraft:*_ore` 不用写 mode 也能用。

### 查询示例

```text
# 任何名字里带 "Sections" 的标签
name=Sections

# 值恰好是 minecraft:stone
value=minecraft:stone mode=exact

# 正则：所有 minecraft:xxx_block
value=^minecraft:.+_block$ mode=regex type=string

# 只搜方块实体里的物品 id
path=block_entities.Items[*].id value=minecraft:diamond

# 只搜 POI 数据，名字里含 portal
root=poi name=portal

# 裸模式：名字或值里出现 diamond 就算
diamond
```

### 名和值分开命中（`chunk=1`）

严格查询要求同一个标签既满足 `name` 又满足 `value`。但 NBT 里很多字段天生是分开存的：告示牌把 `minecraft:sign` 存在 `id`，文字存在 `front_text.messages`；箱子把物品种类存在 `Items[*].id`，数量存在 `Items[*].Count`。这类目标用严格查询永远是 0。

`chunk=1`（GUI 复选框「名和值可分开命中」）把查询拆成两半，只要两半在同一区块里各自都能找到就算命中：

```text
# 严格：0 处，因为没有任何标签叫 sign 且值含 world
name=sign;value=world

# 分区匹配：2 个区块，sign 命中 id=minecraft:sign，world 命中 front_text.messages
name=sign;value=world;chunk=1
```

`chunk=1` 时 `path` 会被忽略（区块级匹配没有位置概念），`limit` 仍然生效。GUI 里这个开关默认打开，随「查找时在 NBT 结构中展开命中」一起决定是否跳转到命中区块并展开。

零结果时对话框会给出诊断，说明是路径锁死、字段名写错、字段值没出现，还是「名和值在不同标签上、需要开分区匹配」。

### 替换语法（CLI `--fields` / GUI 替换框）

```text
<查询> => <替换串>
```

可用 `@name` / `@value` / `@both` 前缀强制指定替换对象：

```text
# 所有值恰好是 minecraft:stone 的标签改成 minecraft:dirt
value=minecraft:stone mode=exact => minecraft:dirt

# 键名由 "old_key" 改成 "new_key"
@name value=old_key => new_key

# 正则 + 捕获组：minecraft:diamond_block → mca:diamond
value=minecraft:(.+)_block mode=regex => mca:$1

# 名字和值同时替换
@both value=minecraft:stone => minecraft:dirt
```

## 三、GUI 用法

`工具 → 查找与替换`（快捷键 `Shift+Cmd+H`，Windows/Linux 为 `Shift+Ctrl+H`；菜单项原名 `NBT Studio`，功能未变，只是名字改成与窗口里做的事一致）：

对话框与 NBT Studio 的查找窗口一致，只保留四个输入项：

- **名字**：标签名模式，留空即不限；默认大小写不敏感、包含即命中。
- **值**：标签值模式，留空即不限；两个框至少要填一个。两个框都填时，只要求两部分都出现在同一块区块里，不要求落在同一个标签上（告示牌的名字与文字本来就分处不同位置）。
- **正则表达式**：勾选后两个框都按 Java 正则匹配，替换串支持 `$1`、`$2` 捕获组。
- **同时处理世界数据文件**：默认勾选，作用范围见第三节。
- **替换为**：替换串，只做查找和预览时可以留空。

底部三个动作按钮，全部作用于查询能触及的所有数据：

| 按钮 | 行为 |
| --- | --- |
| 查找 | 把命中的区块变成地图选区，并在同一个对话框下半部列出命中的区块与数据文件；不修改任何数据，也不关闭对话框 |
| 预览 | 列出将要改动的标签（文件名、路径、旧值、新值），不写盘 |
| 替换 | 先弹「替换 NBT 数据」确认（写明涉及多少个单位、不可撤销），确认后改写区块文件与数据文件 |

没有「全部查找」「全部替换」之类的第二档按钮：查找与替换本身已经是全量操作，多一个按钮只会让人以为存在只处理一半的同名动作。破坏性按钮也不是默认按钮，在输入框里按回车不会触发替换，回车等于取消。

### 世界数据文件

勾选「同时处理世界数据文件」后，查询与替换除了区块文件，还会覆盖：

- `level.dat`（整份世界设置）；
- `data/`（`raids.dat`、`map_*.dat`、`scoreboard.dat` 等）；
- `playerdata/`（每个玩家一份）；
- `DIM*`/`dimensions/` 下的 `data/` 与 `playerdata/`（下界、末地与自定义维度）。

数据文件里放的是整棵标签树而非区块，因此：

- 匹配以文件为单位统计，与区块命中一起出现在同一个对话框下半部的「数据文件」页里（文件、路径、值），可右键复制文件路径或路径串；
- 每个数据文件都能像区块一样展开编辑：在结果区双击一行，或选中后点结果区右上角的「打开」，都会进入该文件的树编辑器（同样的 `NBTTreeView` + 查找栏），标签可改名、改值、增删（与区块编辑器同一套操作条，覆盖全部 12 种类型），查询串已自动填入并展开命中处；保存即按原压缩方式写回原文件；
- 结构类文件（`structures/*.nbt`、`generated/minecraft/structures/`、数据包里的 `structures/`）同样在收集范围内，编辑方式与 `level.dat` 一致；
- 替换按原压缩方式写回（gzip / zlib / 不压缩），先写临时文件再原子替换，不会改动文件根的标签名，也不会把压缩过的文件写成本地未压缩格式；编辑器里手动保存走同一条写回路径；
- 单个文件读写失败不会中断整轮，失败清单会在结果与预览里列出。

### 标签树的增删改（区块与数据文件一致）

查找替换负责「按条件批量改」，但改 NBT 更多时候是「打开一个节点，手动加一个标签、删一个节点」——两个编辑器里的这条操作条就是干这个的，区块与数据文件完全一致（同一份组件，不是两套实现）：

| 控件 | 行为 |
| --- | --- |
| 红色垃圾桶 | 删除当前选中的标签；根节点没有父节点，区块编辑器里根节点不可删，数据文件编辑器里根节点一律不可删 |
| `BYTE` / `SHORT` / `INT` / `LONG` / `FLOAT` / `DOUBLE` | 在选中节点下新增对应数值类型的标签，默认加进复合节点 |
| `String` / `List` / `Compound` | 新增字符串、列表、复合节点 |
| `[B]` / `[I]` / `[L]` | 新增 `ByteArray` / `IntArray` / `LongArray` |
| 类型按钮可用性 | 跟随选中节点动态变化：选中复合节点时 12 种全可用；选中列表时按列表元素类型给可用项；没有选中或选中不可含子节点的类型时，退回只有复合可用的兜底状态 |

- 新增的标签会直接出现在树上并被选中，接着改名/改值即可，无需重新打开文件；
- 改动只落在内存树上，仍需点「保存」才写回：区块编辑器写回 region/poi/entities，数据文件编辑器按原压缩方式写回原文件；
- 删除与新增都是单个节点的操作，不做「批量清空」，避免一条命令删掉整棵子树。

### 结果区（与查找框同窗）

查找不再另开窗口：结果就在对话框下半部，输入框、动作按钮和结果始终在同一个窗口里，改完查询再点「查找」就地刷新，不用关掉结果再打开一次：

| 区域 | 内容 |
| --- | --- |
| 顶部 | 命中统计（多少个区块、多少处数据文件匹配、涉及多少个文件），有截断或失败时一并说明 |
| 结果区右上角 | 「打开」打开当前页选中的那一行，未选中时不可用 |
| 「区块」页 | 每个命中区块一行（X / Z），双击打开该区块的 NBT 编辑器，查询已填入、命中已展开，可用编辑器里的箭头在全部命中之间跳转 |
| 「数据文件」页 | 每处命中一行（文件、路径、值），双击打开该文件的树编辑器 |
| 底部按钮栏 | 「查找 / 预览 / 替换 / 取消」；查找和预览留在原窗口，替换写入后关闭 |

窗口初始只装得下输入框，第一次查找后自动加宽加高并变为可拖拽调整大小。只有命中或只命中数据文件时，页签会自动切到「数据文件」；区块与文件都为空时仍然填充结果区并显示「没有匹配」。

预览同样只弹一个窗口：区块改动在「区块」页，数据文件改动在「数据文件」页（文件、路径、旧值、新值），双击数据文件行可直接进入编辑。

### 文件浏览器（不用搜索也能编辑）

查找结果只包含「查询命中的文件」，而改 NBT 常常是「先把世界里的文件翻一遍再决定改哪个」——这时候没有查询可写。因此除了查找入口，还有一条独立的浏览入口：

- 菜单位置：**工具 → 浏览 NBT 文件…**（`Shift+Cmd/Ctrl+B`）；
- 内容是这个世界里全部可编辑的 NBT 文件，与「同时处理世界数据文件」用的是同一套收集逻辑：`level.dat`、`data/*.dat`、`playerdata/*.dat`、`DIM-1`/`DIM1` 等维度的同名目录、`structures/*.nbt` 与数据包里的结构文件；
- 按目录分组、组内按路径排序，世界根目录（`level.dat` 所在）排在最前；每行显示文件、完整相对路径、大小与修改时间；
- 双击一行或选中后点「打开」即进入该文件的树编辑器（与结果区双击打开的是同一个编辑器），编辑器关掉后浏览器还开着，可以接着开下一个文件；保存后列表会自动重扫，大小与时间随之更新；
- 顶部的「筛选」只是在同一份列表上过滤（匹配文件名或路径的任意片段、不分大小写），筛掉的行不会限制编辑范围——清空筛选立刻回到全部文件；
- 「打开 / 重新扫描」都只是本地列表操作，不触发批量任务，也不会改动文件。

两条入口的分工：要找「满足某个条件的东西」→ 用查找；要「按目录浏览并挑一个文件来改」→ 用浏览。

## 四、CLI 用法

```bash
# 1) 查询：哪些区块含有 minecraft:stone（输出 CSV 选区）
java -jar mcaselector-plus-2.9-plus-1.0-all.jar --mode select \
  -w /path/to/world --query 'NBT contains "value=minecraft:stone"' -o stone.csv

# 2) 批量替换整个世界
java -jar mcaselector-plus-2.9-plus-1.0-all.jar --mode change \
  -w /path/to/world --fields 'NbtReplace="value=minecraft:stone mode=exact => minecraft:dirt"'

# 3) 只替换选区内的区块
java -jar mcaselector-plus-2.9-plus-1.0-all.jar --mode change \
  -w /path/to/world -s selection.csv \
  --fields 'NbtReplace="value=minecraft:stone mode=exact => minecraft:dirt"'

# 4) POI 里名字含 portal 的标签
java -jar mcaselector-plus-2.9-plus-1.0-all.jar --mode select \
  -w /path/to/world --query 'NBT contains "root=poi name=portal"' -o poi.csv
```

注意：`--fields` 的值里有空格和 `=`，必须整体用双引号包住（见上面示例）。

## 五、实现位置

| 文件 | 作用 |
| --- | --- |
| `nbtstudio/NbtQuery.java` | 查询串解析与校验（name/value/path/mode/case/root/type/limit） |
| `nbtstudio/NbtPattern.java`、`NbtMatchMode.java` | 四种匹配模式：contains / exact / glob / regex |
| `nbtstudio/NbtPath.java` | 路径解析与匹配（`a.b[*].c`） |
| `nbtstudio/NbtSearcher.java`、`NbtSearchResult`、`NbtSearchHit` | 遍历 region/poi/entities 三个根，产出命中位置与上下文 |
| `nbtstudio/NbtReplacer.java`、`NbtReplaceResult` | 按名字/值/两者替换，支持捕获组与键重命名 |
| `nbtstudio/NbtValues.java` | 标签值的文本化（数字/字符串/数组/列表） |
| `filter/filters/NbtFilter.java` | 接入过滤器体系（`NBT` 类型，CONTAINS / CONTAINS_NOT） |
| `changer/fields/NbtReplaceField.java` | 接入字段体系（`NbtReplace`），CLI 与 GUI 共用 |
| `ui/dialog/NbtStudioDialog.java` | 查找替换对话框 |
| `ui/dialog/NbtStudioReplaceConfirmationDialog.java` | 替换前的确认框（替换 NBT 数据，含数据文件计数） |
| `ui/dialog/NbtStudioResultPane.java` | 结果区组件：区块页 + 数据文件页，嵌在查找对话框里，双击或「打开」进入对应编辑器 |
| `ui/NbtStudioFinder.java` | 查找两路的执行体（区块选区与地图跳转、数据文件扫描），供对话框在不关窗的情况下调用 |
| `ui/dialog/NbtFileEditorDialog.java` | 单个数据文件/结构文件的树编辑器，按原压缩方式保存 |
| `ui/component/NBTEditorOptionsBar.java` | 标签树增删操作条（红垃圾桶 + 12 种类型新增，随选中动态启停），区块编辑器与数据文件编辑器共用 |
| `ui/dialog/NbtFileBrowserDialog.java` | 全部数据文件的浏览器：按目录分组、筛选、双击进编辑器，保存后自动重扫 |
| `nbtstudio/NbtFileListing.java` | 把文件列表按目录分组并应用筛选（纯逻辑，可单测） |
| `nbtstudio/NbtDataFiles.java` | 数据文件的收集、查找与按原压缩方式写回 |
| `ui/DialogHelper.java#openNbtStudio` | 把对话框结果转成「选区」「预览」或「字段任务」，并驱动数据文件那一路 |
| `ui/DialogHelper.java#openNbtFileBrowser` | 打开文件浏览器（收集全部数据文件后交给对话框） |
| `ui/component/OptionBar.java` | 菜单项与快捷键 |

## 六、构建

需要 JDK 21（JavaFX 由 Gradle 依赖提供）。

```bash
./gradlew shadowJar          # 产出 build/libs/mcaselector-2.9-all.jar（发布时重命名为 mcaselector-plus-2.9-plus-1.0-all.jar）
./gradlew test               # 单元测试
```

运行 GUI：

```bash
java -jar build/libs/mcaselector-2.9-all.jar
```

## 七、验证情况

- `./gradlew test`：14 个用例全部通过
  - `NbtStudioCoreTest`（10 项）：裸模式、name/value 组合、glob、regex、路径限定、root 掩码、类型过滤、名字替换、值替换、正则捕获组替换、非法查询报错；
  - `NbtReplaceFieldTest`（4 项）：字段解析 + 真实 chunk 树上的值替换 / 键改名 / 正则捕获组替换 / 非法 spec 拒绝。
- 真实世界（279 MB 存档副本）端到端：
  - `--query 'NBT contains "value=minecraft:stone"'` → 命中 184 个区块；
  - `--mode change --fields 'NbtReplace="value=minecraft:stone => minecraft:dirt"'` → 19 个 region 全部重写成功；
  - 复检：`value=minecraft:stone` 归零，`value=minecraft:dirt` 由 3536 增至 3720（增量正好 184）。
- 名字 / 裸模式 / POI 作用域在真实世界上同样验证通过（`name=Sections` 57、`value=minecraft:plains` 3200、`minecraft:air` 5242）。
- 世界数据文件（2.13 起）离线自测：造 gzip 的 `level.dat`、zlib 的 `data/foo.dat`、未压缩的 `playerdata/0000.dat` 各一份
  - 查找 `value=hello` → 3 个文件 6 处命中，0 错误；
  - 预览 → 6 处改动、3 个文件将变更，磁盘字节完全未变；
  - 执行 → 6 处写入成功，读回三份文件的压缩方式仍分别是 gzip / zlib / 无，根标签名与其余标签（`Count=3`）原样保留；
  - 复检 → 命中归零，说明替换已落到所有三种压缩形态上。
- 结果窗口与文件编辑器（2.14 起）：离线截图自检——结果窗口两个页签（`区块 (4)` / `数据文件 (4)`）、表头与行内容；gzip 的 `level.dat` 经树编辑器打开再保存，文件头仍是 `1f 8b`，读回 `GameType` 原样，说明手动保存走的是原压缩写回。
- 结果与查找框合窗（2.15 起）：
  - 动手改：原来查找后另开的结果窗口删除，改为嵌在对话框里的 `NbtStudioResultPane`；查找按钮吃掉 JavaFX 的按钮动作，不再关闭对话框；
  - 离线截图自检：同一窗口内上半部是四个输入框与状态行、下半部是「区块 (4)」与「数据文件 (4)」两个页签，表内行内容正常，结果区右上「打开」按钮完整显示且未选中时不可用；页签切换后数据文件表（文件 / 路径 / 值）同样正常；
  - 组件级测试 6 项：填充前隐藏、填充后区块与文件行数一致、只命中文件时自动切到「数据文件」页、空结果仍显示「没有匹配」、顶部统计含区块数与数据文件处数、行数据可取出用于打开编辑器；
  - 全部既有测试仍通过（`NbtStudioCoreTest` 23、`NbtDataFilesStructureTest` 2、`NbtReplaceFieldTest` 6、`NbtEditor*` 15）。
- 文件浏览器入口（2.16 起）：
  - 新增菜单项与 `Shift+Cmd/Ctrl+B` 快捷键，世界未打开时与其他世界相关菜单一样置灰；
  - 组件级测试 10 项（`NbtFileListingTest`）：无筛选时列出全部文件、世界根目录排最前、按目录分组且组内有序、筛选只保留命中文件、筛选忽略大小写、按目录片段筛选、空白筛选等同不过滤、无命中返回空、空输入不抛异常、路径归属判断；
  - 离线截图自检：造 7 个文件（`level.dat`、`data/idcounts.dat`、`data/map_0.dat`、`data/raids.dat`、`playerdata/*.dat`、`structures/*.nbt`、`DIM-1/data/raids.dat`），浏览器一打开就列出全部 7 个、5 个分组，统计显示「7 个文件」；输入 `raids` 后只剩 `data/raids.dat` 与 `DIM-1/data/raids.dat` 两行，输入不存在的名字时表内为空并提示「没有匹配筛选条件的文件。」；
  - 真实世界（`Skyline Sprint [1.1.0]-Realms`）复核：一打开就列出 108 个文件、6 个分组（世界根、`data`、`playerdata`、`generated/minecraft/structures`、`DIM-1/data`、`DIM1/data`），筛选 `raids` 后只剩 3 个（`data/raids.dat`、`DIM-1/data/raids.dat`、`DIM1/data/raids_end.dat`），计数显示「3 / 108 个文件」；
  - 全部测试 63 项通过（1 项跳过）。

- 菜单改名与全类型增删改整合（Plus 层迭代）：
  - 菜单项由 `NBT Studio` 改为 `查找与替换`（`Shift+Cmd/Ctrl+H` 不变），19 份语言文件同步：`zh_CN` = 查找与替换、`zh_TW` = 查找與替換、其余 17 份 = Find and Replace，无一处残留旧名；
  - 增删操作条抽成公共组件 `NBTEditorOptionsBar`，区块编辑器的三个页签（region / poi / entities）与数据文件编辑器（`level.dat`、`data/*.dat`、`playerdata/*.dat`、结构文件）共用同一份实现；
  - headless 实测数据文件编辑器：12 个新增类型按钮全部可用（`ADD buttons=12 enabled=12`），未选中时退回复合兜底；根节点垃圾桶不可用（`DELETE disabled at root=true`）；对 `level.dat` 新增再删除后保存，读回键集合仍为 `[RandomSeed, LevelName]`，证明增删只作用于所选节点且写回正确；
  - headless 实测区块编辑器：三个页签各有 13 个按钮（1 删除 + 12 新增），切换页签后按钮状态随之重算；
  - 全量测试 63 项通过、1 项跳过（`skipOnMac` 的 GUI 用例）：`NbtEditorTabSwitchTest`、`NbtEditorTabSwitchRealWorldTest`、`NbtEditorChunkWalkTest`、`NbtStudioResultPaneTest`、`NbtStudioMergedWindowShotTest`、`NbtFileListingTest`、`NbtDataFilesStructureTest`、`NbtStudioCoreTest`、`NbtReplaceFieldTest`；
  - 交付件：`build/libs/mcaselector-plus-2.9-plus-1.0-all.jar`（19.4 MB，`Application-Version: 2.9` / `Plus-Version: 1.0`，可直接 `java -jar` 启动）。

## 八、说明

- 本项目是 MCA Selector 的修改分支，遵循原项目的 MIT 许可证与署名要求（上游版权归 Querz，Plus 层归本仓库作者）。
- 内部 Java 包名（`net.querz.mcaselector`）、配置目录与缓存路径保持 `mcaselector` 不变，以便与已安装的上游版本共用配置；
  对外的名称统一为 **MCA Selector Plus**：应用名、窗口标题、关于对话框、安装包文件名、Windows 默认安装目录
  （`%LOCALAPPDATA%\Programs\MCA Selector Plus`）、macOS 应用包名与 Linux 的 `/opt/mcaselector-plus`、
  `/usr/bin/mcaselector-plus` 都带 Plus 标识。
