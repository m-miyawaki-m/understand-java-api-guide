# csv-all-js コマンド 詳細設計書（JavaScript / jQuery 対応）

## 1. 背景と設計意図

### 1.1 Java 版との違い

Java では「クラス」がコードの構造単位だったが、JavaScript（特に jQuery ベースの既存プロジェクト）では **JSファイルがクラスに相当する構造単位** となる。

| 観点 | Java | JavaScript (jQuery) |
|------|------|-------------------|
| 構造単位 | クラス | JSファイル |
| 関数の所属 | クラスに属する | ファイルに属する（グローバル関数 or オブジェクトのプロパティ） |
| ファイル間の接続 | import 文 | HTML の `<script src>` タグ |
| イベントバインド | なし（リスナーパターンは別途） | `$(selector).on(event, handler)` が主要パターン |

### 1.2 HTML 起点の分析が必要な理由

jQuery プロジェクトでは、HTML ファイルが **エントリーポイント** であり、`<script src="...">` で必要な JS ファイルを読み込む。

```
index.html
  ├── <script src="lib/jquery.js">      ← ライブラリ（分析対象外）
  ├── <script src="js/taskManager.js">  ← 分析対象
  ├── <script src="js/uiHelper.js">     ← 分析対象
  └── <script src="js/app.js">          ← 分析対象
```

同じ関数名が複数の JS ファイルに存在しうるため、**どの HTML がどの JS をインポートしているか** を起点にして、呼び出し元を絞り込む必要がある。

### 1.3 .on() バインディングの分析が必要な理由

jQuery プロジェクトのビジネスロジックの大部分は、イベントハンドラ内に記述される。

```javascript
$('.btn-save').on('click', function() {
    var data = FormHelper.collectData();    // ← この呼び出し関係を追いたい
    TaskManager.save(data);                 // ← これも
});
```

`.on()` のハンドラは無名関数であるため、通常の `call` 参照だけでは **「どのUI要素のイベントが起点か」** がわからない。セレクタ（`.btn-save`）とイベント種別（`click`）を紐づけて出力することで、UI → ロジック の接続が追跡可能になる。

---

## 2. コマンド仕様

### 2.1 コマンドライン

```
java -cp "Understand.jar;." DependencyAnalyzer <UDBファイルパス> csv-all-js <HTMLファイル名> <出力ディレクトリ>
```

| 引数 | 説明 |
|------|------|
| UDBファイルパス | Understand データベース（`.und`）のパス |
| `csv-all-js` | JS/jQuery 用 CSV 一括出力コマンド |
| HTMLファイル名 | 分析起点となる HTML ファイルの名前（例: `index.html`）。UDB 内のファイルエンティティ名と一致させる |
| 出力ディレクトリ | CSV ファイルの出力先ディレクトリ |

### 2.2 出力ファイル

| ファイル名 | 内容 | Java版との対応 |
|-----------|------|---------------|
| `files.csv` | JSファイル一覧（HTML がインポートしているもの） | `classes.csv` に相当 |
| `functions.csv` | 関数定義一覧 | `methods.csv` に相当 |
| `calls.csv` | 関数呼び出し一覧 | `calls.csv` と同等 |
| `events.csv` | jQuery .on() イベントバインド一覧 | **新規**（Java版にはなし） |

---

## 3. 分析の全体フロー

```
                                     UDB (Understand DB)
                                           │
                         ┌─────────────────┤
                         ▼                  ▼
                   HTML ファイル       全 JS ファイル
                   (引数で指定)
                         │
                    depends()
                         │
                         ▼
              ┌── インポート対象の JS ファイル集合（Set<Entity>）──┐
              │                                                   │
    ┌─────────┼──────────┬──────────────┐                        │
    ▼         ▼          ▼              ▼                        │
files.csv  functions.csv  calls.csv   events.csv                 │
              │            │              │                       │
              │      呼び出し元を          │                       │
              │    importedFiles で       │                       │
              │      フィルタ             │                       │
              │            │        Lexer で .on() の             │
              │            │        セレクタを抽出                │
              └────────────┴──────────────┘                       │
                                                                  │
                           同名関数は JSファイル名で区別 ←─────────┘
```

### 3.1 ステップ1: HTML のインポート対象 JS ファイルを特定する

```java
// 引数で指定された HTML ファイル名に一致するエンティティを見つける
Entity htmlFile = null;
for (Entity file : db.ents("file ~unknown ~unresolved")) {
    if (file.name().equals(htmlFileName)) {
        htmlFile = file;
        break;
    }
}

// HTML が依存している JS ファイルを取得
Set<String> importedFileNames = new HashSet<>();
Map<Entity, Reference[]> deps = htmlFile.depends();
for (Entity dep : deps.keySet()) {
    if (dep.name().endsWith(".js")) {
        importedFileNames.add(dep.name());
    }
}
```

**`depends()` を使う理由**: HTML の `<script src="...">` は Understand によってファイル間依存として解析される。`depends()` で HTML → JS の依存関係を取得すれば、`<script src>` で読み込まれている JS ファイルの一覧が得られる。

> **要検証**: Understand が HTML の `<script src>` を `depends()` で返すかどうかは、実際の UDB で確認が必要。もし返さない場合は、HTML ファイルの Lexer でトークンを走査し、`<script src="...">` のパターンを自力でパースする代替案がある（後述: セクション 8.1）。

### 3.2 ステップ2: 呼び出し元フィルタリングの考え方

**要件**: `calls.csv` に出力する呼び出し関係の「呼び出し元」は、ステップ1 で特定したインポート対象 JS ファイル内の関数に限定する。

```
全関数の呼び出し関係:
  jquery.js::ajax()  →  TaskManager.save()     ← 対象外（ライブラリ）
  app.js::init()     →  TaskManager.load()     ← 対象（インポート対象）
  other.js::foo()    →  TaskManager.load()     ← 対象外（HTMLが読み込んでいない）
```

フィルタリング方法:

```java
// 関数が属するファイルがインポート対象かを判定
private static boolean isInImportedFile(Entity function, Set<String> importedFileNames) {
    Reference[] defInRefs = function.refs("definein", "file", true);
    if (defInRefs.length > 0) {
        return importedFileNames.contains(defInRefs[0].ent().name());
    }
    return false;
}
```

### 3.3 名前空間の扱い

JavaScript（jQuery）プロジェクトでは、グローバルスコープ汚染を避けるためにオブジェクトで名前空間を作るパターンが一般的。

```javascript
// パターン1: オブジェクトリテラルによる名前空間
var TaskManager = {
    save: function() { ... },    // 名前空間: TaskManager
    load: function() { ... }     // 名前空間: TaskManager
};

// パターン2: ネストした名前空間
var MyApp = {};
MyApp.Util = {
    format: function() { ... }   // 名前空間: MyApp.Util
};

// パターン3: 名前空間なし（グローバル関数）
function init() { ... }          // 名前空間: (空)
```

**Understand API での名前空間取得**:

Understand は関数の `longname()` に所属先を含めた完全修飾名を返す。

| `longname()` | `name()` | 名前空間（差分） |
|---------------|----------|-----------------|
| `TaskManager.save` | `save` | `TaskManager` |
| `MyApp.Util.format` | `format` | `MyApp.Util` |
| `init` | `init` | （空） |

名前空間は `longname()` から `name()` を除いた部分として抽出できる。

```java
private static String getNamespace(Entity func) {
    String longName = func.longname();
    String shortName = func.name();
    // longname が "TaskManager.save" で name が "save" なら
    // namespace は "TaskManager"
    if (longName.endsWith("." + shortName)) {
        return longName.substring(0, longName.length() - shortName.length() - 1);
    }
    return "";
}
```

> **要検証**: Understand が JS の `longname()` をどのようなフォーマットで返すか。ファイルパスが先頭に付く場合（例: `js/taskManager.js.TaskManager.save`）は、ファイル名部分を除去するロジックが必要。その場合は `definein` で所属ファイルを取得し、`file.longname()` のプレフィックスを除去する方が確実。

```java
// ファイルプレフィックス付きの場合の代替ロジック
private static String getNamespace(Entity func, Entity ownerFile) {
    String longName = func.longname();
    String shortName = func.name();
    String filePrefix = ownerFile.longname() + ".";

    // ファイルパスプレフィックスを除去
    if (longName.startsWith(filePrefix)) {
        longName = longName.substring(filePrefix.length());
    }

    // 残りから関数名を除いた部分が名前空間
    if (longName.endsWith("." + shortName)) {
        return longName.substring(0, longName.length() - shortName.length() - 1);
    }
    return "";
}
```

### 3.4 同名関数の扱い

**要件**: 別ファイルに同じ関数名がある場合は、それぞれ別行として出力する。名前空間が異なる場合も別行。

```
app.js         に init() がある           → 名前空間なし
uiHelper.js    に init() がある           → 名前空間なし
taskManager.js に TaskManager.save() がある → 名前空間: TaskManager
formHelper.js  に FormHelper.save() がある  → 名前空間: FormHelper
```

→ `functions.csv` には4行出力される:

```csv
JSファイル名,名前空間,関数名,ファイルパス,定義行
app.js,,init,js/app.js,5
uiHelper.js,,init,js/uiHelper.js,12
taskManager.js,TaskManager,save,js/taskManager.js,15
formHelper.js,FormHelper,save,js/formHelper.js,8
```

→ `calls.csv` でもファイル名 + 名前空間で区別される:

```csv
呼び出し元ファイル,呼び出し元名前空間,呼び出し元関数,呼び出し先ファイル,呼び出し先名前空間,呼び出し先関数,ファイル名,呼び出し行
app.js,,init,taskManager.js,TaskManager,load,app.js,8
uiHelper.js,,init,taskManager.js,TaskManager,render,uiHelper.js,15
```

**設計上のポイント**: Java 版では「クラス名 + メソッド名」で一意にしていたが、JS 版では「JSファイル名 + 名前空間 + 関数名」の3要素で一意にする。名前空間は `longname()` と `name()` の差分から導出する。

---

## 4. 各CSVの詳細設計

### 4.1 files.csv（JSファイル一覧）

**目的**: 指定 HTML がインポートしている JS ファイルの一覧。分析対象のスコープを明示する。

**ヘッダ**: `JSファイル名,関数数,ファイルパス`

| 列 | 取得元 | API |
|----|--------|-----|
| JSファイル名 | ファイルの短縮名 | `file.name()` |
| 関数数 | ファイル内の関数定義数 | `file.refs("define", "function", true).length` |
| ファイルパス | ファイルの相対パス | `file.longname()` |

**データ取得ロジック**:

```
1. htmlFile.depends() で依存先を取得
2. 依存先のうち .js で終わるものをフィルタ
3. 各 JS ファイルの関数数を refs("define", "function", true).length で計算
```

**出力例**:

```csv
JSファイル名,関数数,ファイルパス
taskManager.js,5,js/taskManager.js
uiHelper.js,3,js/uiHelper.js
app.js,2,js/app.js
```

### 4.2 functions.csv（関数定義一覧）

**目的**: インポート対象 JS ファイル内のすべての関数定義を一覧化する。名前空間付きで出力し、同名関数を区別可能にする。

**ヘッダ**: `JSファイル名,名前空間,関数名,ファイルパス,定義行`

| 列 | 取得元 | API |
|----|--------|-----|
| JSファイル名 | 所属ファイルの短縮名 | `jsFile.name()` |
| 名前空間 | 関数が属するオブジェクト/モジュール名（なければ空） | `getNamespace(func)` — `longname()` と `name()` の差分 |
| 関数名 | 関数の短縮名 | `func.name()` |
| ファイルパス | ファイルの相対パス | `jsFile.longname()` |
| 定義行 | 定義の行番号 | `ref.line()` |

**データ取得ロジック**:

```
1. 各インポート対象 JS ファイル jsFile に対して
2. jsFile.refs("define", "function", true) で関数定義を取得
3. 各 ref について:
   - func = ref.ent()
   - 関数名 = func.name()
   - 名前空間 = getNamespace(func) — longname() から name() を除いた部分
   - 定義行 = ref.line()
```

**JavaScript での関数パターンと名前空間の対応**:

```javascript
// パターン1: グローバル関数 → 名前空間なし
function init() { ... }
// → JSファイル名: app.js, 名前空間: (空), 関数名: init

// パターン2: オブジェクトプロパティ → オブジェクト名が名前空間
var TaskManager = {
    save: function() { ... },
    load: function() { ... }
};
// → JSファイル名: taskManager.js, 名前空間: TaskManager, 関数名: save
// → JSファイル名: taskManager.js, 名前空間: TaskManager, 関数名: load

// パターン3: ネストしたオブジェクト → ドット連結の名前空間
var MyApp = {};
MyApp.Util = {
    format: function() { ... }
};
// → JSファイル名: util.js, 名前空間: MyApp.Util, 関数名: format

// パターン4: プロトタイプ定義 → コンストラクタ名が名前空間
function Task(name) { this.name = name; }
Task.prototype.getName = function() { return this.name; };
// → JSファイル名: task.js, 名前空間: Task, 関数名: getName

// パターン5: 無名関数（イベントハンドラ等）
$('.btn').on('click', function() { ... });
// → Understand がどう命名するかは要検証
```

> **要検証**: Understand が JavaScript の `longname()` をどのフォーマットで返すか（ファイルパスプレフィックスの有無）。また無名関数をどのような名前で Entity 化するか。実際の UDB で確認が必要。

**出力例**:

```csv
JSファイル名,名前空間,関数名,ファイルパス,定義行
taskManager.js,TaskManager,save,js/taskManager.js,15
taskManager.js,TaskManager,load,js/taskManager.js,25
taskManager.js,TaskManager,findById,js/taskManager.js,35
uiHelper.js,UIHelper,showMessage,js/uiHelper.js,5
uiHelper.js,UIHelper,clearForm,js/uiHelper.js,12
app.js,,init,js/app.js,3
app.js,,setupEvents,js/app.js,10
```

### 4.3 calls.csv（関数呼び出し一覧）

**目的**: インポート対象 JS ファイル内の関数が呼び出している他の関数を一覧化する。名前空間付きで出力。

**ヘッダ**: `呼び出し元ファイル,呼び出し元名前空間,呼び出し元関数,呼び出し先ファイル,呼び出し先名前空間,呼び出し先関数,ファイル名,呼び出し行`

| 列 | 取得元 | API |
|----|--------|-----|
| 呼び出し元ファイル | 呼び出し元関数が属する JS ファイル名 | `jsFile.name()`（外側ループ） |
| 呼び出し元名前空間 | 呼び出し元関数の名前空間 | `getNamespace(func)` |
| 呼び出し元関数 | 呼び出しを行っている関数 | `func.name()` |
| 呼び出し先ファイル | 呼び出し先関数が属する JS ファイル名 | `getOwnerFileName(calledFunc)` |
| 呼び出し先名前空間 | 呼び出し先関数の名前空間 | `getNamespace(calledFunc)` |
| 呼び出し先関数 | 呼び出されている関数 | `calledFunc.name()` |
| ファイル名 | 呼び出しが発生しているファイル | `callRef.file().name()` |
| 呼び出し行 | 呼び出しの行番号 | `callRef.line()` |

**データ取得ロジック（3段ネスト）**:

```
1. 各インポート対象 JS ファイル jsFile
2.   → jsFile.refs("define", "function", true) で関数一覧
3.     各関数 func
4.       → func.refs("call", "function", true) で呼び出し先一覧
5.         各呼び出し先 calledFunc
6.           → getOwnerFileName(calledFunc) で所属ファイルを逆引き
7.           → getNamespace(calledFunc) で名前空間を取得
```

```
JSファイル ──[define]──→ 関数 ──[call]──→ 呼び出し先関数
   │                      │                    │
   │                      │                    ├─ 所属ファイルを definein で逆引き
   │                      │                    └─ 名前空間を longname() - name() で導出
   │                      ├─ 呼び出し元の情報
   │                      └─ 呼び出し元の名前空間
   └─ 呼び出し元ファイルの情報
```

**呼び出し元フィルタ**: 外側ループがインポート対象 JS ファイルに限定されているため、自動的にフィルタされる。呼び出し先はフィルタしない（外部ライブラリの関数を呼んでいる場合も記録する価値がある）。

**出力例**:

```csv
呼び出し元ファイル,呼び出し元名前空間,呼び出し元関数,呼び出し先ファイル,呼び出し先名前空間,呼び出し先関数,ファイル名,呼び出し行
app.js,,init,taskManager.js,TaskManager,load,app.js,8
app.js,,init,uiHelper.js,UIHelper,showMessage,app.js,9
app.js,,setupEvents,taskManager.js,TaskManager,save,app.js,15
taskManager.js,TaskManager,save,formHelper.js,FormHelper,validate,taskManager.js,20
```

### 4.4 events.csv（jQuery .on() イベントバインド一覧）

**目的**: jQuery の `.on()` でバインドされているイベントハンドラと、そのハンドラ内で呼び出している関数を一覧化する。

**ヘッダ**: `セレクタ,イベント,呼び出し先ファイル,呼び出し先名前空間,呼び出し先関数,ファイル名,呼び出し行`

| 列 | 説明 |
|----|------|
| セレクタ | jQuery セレクタ文字列（例: `.btn-save`, `#submit-form`） |
| イベント | イベント種別（例: `click`, `submit`, `change`） |
| 呼び出し先ファイル | ハンドラ内で呼ばれている関数の所属ファイル |
| 呼び出し先名前空間 | ハンドラ内で呼ばれている関数の名前空間 |
| 呼び出し先関数 | ハンドラ内で呼ばれている関数名 |
| ファイル名 | `.on()` が記述されているファイル名 |
| 呼び出し行 | 呼び出しの行番号 |

**出力例**:

```csv
セレクタ,イベント,呼び出し先ファイル,呼び出し先名前空間,呼び出し先関数,ファイル名,呼び出し行
.btn-save,click,taskManager.js,TaskManager,save,app.js,25
.btn-save,click,formHelper.js,FormHelper,collectData,app.js,24
#task-list,change,uiHelper.js,UIHelper,refreshList,app.js,30
```

> `.btn-save` の `click` ハンドラ内で `TaskManager.save()` と `FormHelper.collectData()` が呼ばれている → 2行出力

---

## 5. events.csv の取得アプローチ（.on() バインディング解析）

### 5.1 なぜ Lexer が必要か

`.on()` のセレクタ文字列（`'.btn-save'`）とイベント名（`'click'`）は、Understand の `refs()` API からは取得できない。

- `refs("call", ...)` は `.on()` メソッドの呼び出しを検出できる
- しかし、`$('.btn-save')` のセレクタ文字列はただの引数（文字列リテラル）であり、エンティティとしてモデル化されない
- イベント名（`'click'`）も同様に文字列リテラル

そのため、`.on()` 呼び出しの位置を特定した後、**Lexer API でソースコードのトークンを走査** し、セレクタとイベント名を文字列リテラルから抽出する必要がある。

### 5.2 抽出アルゴリズム

対象となるコードパターン:

```javascript
$('.btn-save').on('click', function() {
    FormHelper.collectData();
    TaskManager.save(data);
});
```

#### ステップ A: `.on()` 呼び出しの位置を特定

```java
// JS ファイル内の全参照から ".on" メソッドの呼び出しを探す
Reference[] allRefs = jsFile.filerefs("call", "function", true);
for (Reference ref : allRefs) {
    if (ref.ent().name().equals("on")) {
        int onCallLine = ref.line();
        int onCallColumn = ref.column();
        // → ステップ B, C へ
    }
}
```

> **要検証**: Understand が jQuery の `.on()` をどのように参照化するか。`on` という名前の関数呼び出しとして認識されるか、あるいは jQuery オブジェクトのメソッドとして認識されるか。

#### ステップ B: `.on()` より前のトークンを遡ってセレクタを抽出

```java
Lexer lexer = jsFile.lexer(true, false, false);
Lexeme onLexeme = lexer.lexeme(onCallLine, onCallColumn);

// .on() の前にある $('...') のセレクタ文字列を探す
// トークンを遡って、直前の String トークン（セレクタ）を見つける
Lexeme lex = onLexeme.previous();
String selector = "";
while (lex != null) {
    if ("String".equals(lex.token())) {
        // $() の引数の文字列リテラル
        // さらに前を確認して $( であることを検証
        selector = lex.text(); // "'.btn-save'" → クォートを除去
        break;
    }
    if ("Newline".equals(lex.token())) break; // 行を超えたら探索中止
    lex = lex.previous();
}
```

#### ステップ C: `.on()` の第1引数からイベント名を抽出

```java
// .on() の直後のトークンを走査してイベント名を見つける
Lexeme lex = onLexeme.next();
String eventName = "";
while (lex != null) {
    if ("String".equals(lex.token())) {
        eventName = lex.text(); // "'click'" → クォートを除去
        break;
    }
    lex = lex.next();
}
```

#### ステップ D: ハンドラ（無名関数）内の呼び出しを取得

`.on()` の第2引数（または第3引数）の無名関数内の呼び出しを取得する。

**アプローチ**:

```
方法1: Understand が無名関数を Entity として認識している場合
  → その Entity の refs("call", "function", true) で呼び出し先を取得

方法2: 無名関数が Entity 化されていない場合
  → .on() の呼び出し行以降のトークンを走査し、
    function() { ... } のブロック内にある関数呼び出しを Lexer で特定
    （Lexeme.entity() で呼び出し先の Entity を取得）
```

> **要検証**: Understand が jQuery の `.on()` のコールバック内の無名関数をどうモデル化するかによって、方法1 か方法2 かが決まる。方法1 が使えれば `refs()` ベースのシンプルな実装になる。

### 5.3 堅牢性の考慮

jQuery の `.on()` には複数の書き方がある:

```javascript
// パターン1: 基本形
$('.btn').on('click', function() { ... });

// パターン2: デリゲート
$('#container').on('click', '.btn', function() { ... });

// パターン3: イベントオブジェクト
$('.btn').on({ click: function() { ... }, hover: function() { ... } });

// パターン4: 変数セレクタ
$(element).on('click', handler);
```

**スコープ**: 本設計ではパターン1（基本形）を主対象とする。パターン2（デリゲート）はイベント名の次の文字列引数としてセレクタが渡されるため、「直後の文字列 = イベント名」の仮定を拡張する必要がある。パターン3, 4 は初期実装ではスコープ外とし、対応できなかった場合はスキップする（エラーにはしない）。

---

## 6. ヘルパーメソッドの設計

### 6.1 `getOwnerFileName(Entity function)` — 所属ファイル逆引き

Java 版の `getOwnerClassName()` に相当。関数がどのファイルに定義されているかを逆引きする。

```java
private static String getOwnerFileName(Entity function) {
    Reference[] defInRefs = function.refs("definein", "file", true);
    if (defInRefs.length > 0) {
        return defInRefs[0].ent().name();
    }
    return "";
}
```

Java 版との違い: `definein` の第2引数が `"class"` → `"file"` に変わる。JS ではファイルがクラスに相当するため。

### 6.2 `findHtmlFile(Database db, String htmlFileName)` — HTML ファイル検索

```java
private static Entity findHtmlFile(Database db, String htmlFileName) {
    for (Entity file : db.ents("file ~unknown ~unresolved")) {
        if (file.name().equals(htmlFileName)) {
            return file;
        }
    }
    return null;
}
```

### 6.3 `getImportedJsFiles(Entity htmlFile)` — インポート対象 JS ファイル取得

```java
private static Set<Entity> getImportedJsFiles(Entity htmlFile) {
    Set<Entity> jsFiles = new LinkedHashSet<>();
    Map<Entity, Reference[]> deps = htmlFile.depends();
    for (Entity dep : deps.keySet()) {
        if (dep.name().endsWith(".js")) {
            jsFiles.add(dep);
        }
    }
    return jsFiles;
}
```

### 6.4 `getNamespace(Entity func)` — 名前空間の抽出

関数の `longname()` と `name()` の差分から名前空間を導出する。

```java
private static String getNamespace(Entity func) {
    String longName = func.longname();
    String shortName = func.name();
    if (longName.endsWith("." + shortName)) {
        String ns = longName.substring(0, longName.length() - shortName.length() - 1);
        // ファイルパスプレフィックスが含まれる場合は除去
        // 例: "js/taskManager.js.TaskManager" → "TaskManager"
        int lastSlash = ns.lastIndexOf('/');
        if (lastSlash >= 0) {
            String afterSlash = ns.substring(lastSlash + 1);
            // "taskManager.js.TaskManager" → ".js." の位置でさらに分割
            int jsExt = afterSlash.indexOf(".js.");
            if (jsExt >= 0) {
                return afterSlash.substring(jsExt + 4); // ".js." の後ろ
            }
        }
        return ns;
    }
    return "";
}
```

> **要検証**: `longname()` のフォーマットによってパース方法が変わる。実際の UDB で以下を確認:
> - `func.longname()` が `"TaskManager.save"` → シンプルな差分で OK
> - `func.longname()` が `"js/taskManager.js.TaskManager.save"` → ファイルパスプレフィックス除去が必要
>
> 確認後にロジックを確定する。

### 6.5 `stripQuotes(String s)` — 文字列リテラルのクォート除去

Lexer から取得した文字列リテラルは `'...'` や `"..."` で囲まれているため、除去するユーティリティ。

```java
private static String stripQuotes(String s) {
    if (s.length() >= 2
        && ((s.startsWith("'") && s.endsWith("'"))
         || (s.startsWith("\"") && s.endsWith("\"")))) {
        return s.substring(1, s.length() - 1);
    }
    return s;
}
```

---

## 7. メソッド構成

### 7.1 全体構成図

```
main()
  └─ switch("csv-all-js")
       └─ exportAllJsCsv(db, htmlFileName, outputDir)    ← オーケストレーター
            │
            ├─ findHtmlFile(db, htmlFileName)
            ├─ getImportedJsFiles(htmlFile)
            │
            ├─ exportFilesCsv(importedFiles, dir)
            ├─ exportFunctionsCsv(importedFiles, dir)
            │    └─ getNamespace(func)                    ← 名前空間抽出
            ├─ exportJsCallsCsv(importedFiles, dir)
            │    ├─ getOwnerFileName(function)            ← 所属ファイル逆引き
            │    └─ getNamespace(func)                    ← 名前空間抽出
            └─ exportEventsCsv(importedFiles, dir)
                 ├─ Lexer API でセレクタ・イベント名を抽出
                 ├─ stripQuotes(text)
                 ├─ getOwnerFileName(function)
                 └─ getNamespace(func)
```

### 7.2 DependencyAnalyzer.java への追加

既存の Java 版コマンド（`file-deps`, `class-deps`, `csv-all`）はそのまま残し、`csv-all-js` を新たに追加する。

```java
case "csv-all-js":
    String htmlFileName = args.length > 2 ? args[2] : null;
    String jsOutputDir = args.length > 3 ? args[3] : null;
    exportAllJsCsv(db, htmlFileName, jsOutputDir);
    break;
```

---

## 8. リスクと代替案

### 8.1 HTML の `<script src>` を `depends()` が返さない場合

Understand が HTML ファイルの `<script src>` をファイル間依存として解析しない可能性がある。

**代替案**: HTML ファイルの Lexer でトークンを走査し、`<script` → `src` → `=` → 文字列リテラル のパターンを自力で検出する。

```java
private static Set<Entity> getImportedJsFilesByLexer(Database db, Entity htmlFile)
        throws UnderstandException {
    Set<String> jsFileNames = new HashSet<>();
    Lexer lexer = htmlFile.lexer(true, false, false);
    Lexeme lex = lexer.first();
    boolean inScript = false;
    boolean foundSrc = false;

    while (lex != null) {
        String text = lex.text();
        if ("<script".equalsIgnoreCase(text) || "script".equalsIgnoreCase(text)) {
            inScript = true;
        } else if (inScript && "src".equalsIgnoreCase(text)) {
            foundSrc = true;
        } else if (foundSrc && "String".equals(lex.token())) {
            String path = stripQuotes(text);
            // パスからファイル名を抽出
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            jsFileNames.add(fileName);
            inScript = false;
            foundSrc = false;
        } else if (">".equals(text)) {
            inScript = false;
            foundSrc = false;
        }
        lex = lex.next();
    }

    // ファイル名から Entity を引き当てる
    Set<Entity> result = new LinkedHashSet<>();
    for (Entity file : db.ents("file ~unknown ~unresolved")) {
        if (jsFileNames.contains(file.name())) {
            result.add(file);
        }
    }
    return result;
}
```

### 8.2 Understand が JavaScript の無名関数を Entity 化しない場合

`.on()` ハンドラ内の呼び出しを `refs()` で取得できない可能性がある。

**代替案**: Lexer API で `.on()` の `function() { ... }` ブロック内のトークンを走査し、`Lexeme.entity()` が `null` でないトークン（= Understand が認識した識別子）から呼び出し先を特定する。

```java
// .on() の function() { 以降のトークンを走査
// { の深さカウンタでブロック範囲を追跡
int depth = 0;
boolean inHandler = false;
Lexeme lex = ...; // .on() の直後から走査

while (lex != null) {
    if ("{".equals(lex.text())) depth++;
    if ("}".equals(lex.text())) {
        depth--;
        if (depth == 0 && inHandler) break; // ハンドラ終了
    }
    if ("function".equals(lex.text())) inHandler = true;

    // Lexeme に紐づく Entity があり、かつ参照が call であれば記録
    if (lex.entity() != null && lex.reference() != null) {
        Reference ref = lex.reference();
        if (ref.kind().name().contains("Call")) {
            // 呼び出し先として記録
        }
    }
    lex = lex.next();
}
```

### 8.3 Understand が HTML + JS を同一 UDB で解析できない場合

Understand は言語ごとにプロジェクトを作成する場合がある。HTML と JS が同一プロジェクトで解析されているかどうかを確認する必要がある。

**対応**: Understand のプロジェクト作成時に、言語設定で `Web` を選択すれば HTML + JavaScript + CSS が統合解析される。

---

## 9. 実装優先順

実際の Understand UDB での動作検証結果に応じて段階的に実装する。

| フェーズ | 内容 | 依存 |
|---------|------|------|
| **Phase 1** | `files.csv` + `functions.csv` の出力 | `depends()` or Lexer フォールバックの確認 |
| **Phase 2** | `calls.csv` の出力（呼び出し元フィルタ付き） | Phase 1 + JS の `refs("call")` の動作確認 |
| **Phase 3** | `events.csv` の出力 | Phase 2 + `.on()` の Lexer 解析の検証 |

各フェーズの完了条件: 実際の UDB でサンプル出力が正しく生成されることを確認する。

---

## 10. API 使用パターンまとめ（Java版との対比）

| 用途 | Java 版 | JS 版 |
|------|---------|-------|
| 構造単位の取得 | `db.ents("class ~unknown ~unresolved")` | `htmlFile.depends()` → `.js` でフィルタ |
| 関数定義の列挙 | `cls.refs("define", "method", true)` | `jsFile.refs("define", "function", true)` |
| 関数呼び出し | `method.refs("call", "method", true)` | `func.refs("call", "function", true)` |
| 所属先の逆引き | `method.refs("definein", "class", true)` | `func.refs("definein", "file", true)` |
| 名前空間の取得 | 不要（クラスが名前空間を兼ねる） | `func.longname()` と `func.name()` の差分 |
| セレクタ・イベント名 | なし | Lexer API でトークン走査 |

**kindstring の違い**: Java では `"class"` / `"method"` だが、JS（Web 言語ファミリー）では `"file"` / `"function"` を使用する。Understand 内部では `Web Javascript Function` 等のプレフィックス付きだが、kindstring フィルタでは部分一致するため `"function"` で十分。

**名前空間の違い**: Java では `cls.longname()` がそのまま名前空間+クラス名になるが、JS では `func.longname()` からファイルパスプレフィックスを除き、`func.name()` を引いた残りが名前空間になる。

---

## 付録 A: Understand API クイックリファレンス

本設計書で使用する Understand Java API のメソッドを、このドキュメント単体で理解できるよう整理する。

### A.1 主要クラスとオブジェクトモデル

```
Understand.open(path)
    └→ Database
         ├→ db.ents(kindstring)           → Entity[]
         ├→ db.close()
         │
         Entity
         ├→ entity.name()                 → String（短縮名: "save"）
         ├→ entity.longname()             → String（完全修飾名: "TaskManager.save"）
         ├→ entity.kind()                 → Kind
         │    └→ kind.name()              → String（"Web Javascript Function"）
         ├→ entity.refs(refkind, entkind, unique) → Reference[]
         ├→ entity.filerefs(refkind, entkind, unique) → Reference[]
         ├→ entity.depends()              → Map<Entity, Reference[]>
         ├→ entity.dependsby()            → Map<Entity, Reference[]>
         └→ entity.lexer(lookupEnts, showInactive, expandMacros) → Lexer
              └→ Lexer
                   ├→ lexer.first()       → Lexeme
                   └→ lexer.lexeme(line, column) → Lexeme
                        └→ Lexeme
                             ├→ lex.text()      → String（トークン文字列）
                             ├→ lex.token()     → String（トークン種別名）
                             ├→ lex.entity()    → Entity（関連エンティティ or null）
                             ├→ lex.reference() → Reference（関連参照 or null）
                             ├→ lex.next()      → Lexeme（次のトークン or null）
                             ├→ lex.previous()  → Lexeme（前のトークン or null）
                             ├→ lex.lineBegin() → int
                             └→ lex.columnBegin() → int

         Reference
         ├→ ref.ent()                     → Entity（参照先エンティティ）
         ├→ ref.file()                    → Entity（参照が記述されているファイル）
         ├→ ref.scope()                   → Entity（参照元エンティティ）
         ├→ ref.line()                    → int（行番号）
         ├→ ref.column()                  → int（列番号）
         └→ ref.kind()                    → Kind（参照の種別）
              └→ kind.name()              → String（"Web Javascript Call"）
```

### A.2 `db.ents(kindstring)` — エンティティ一覧取得

データベース内のエンティティを kindstring フィルタで絞り込んで取得する。

```java
Entity[] db.ents(String kindstring)
```

| パラメータ | 型 | 説明 |
|-----------|-----|------|
| `kindstring` | `String` | エンティティ種別フィルタ。`null` で全エンティティ |
| 戻り値 | `Entity[]` | 条件に一致するエンティティの配列 |

**kindstring 構文**:

| 構文 | 説明 | 例 |
|------|------|-----|
| 単一指定 | その種別のみ | `"function"` |
| 複数指定（カンマ区切り） | いずれかに該当 | `"function, variable"` |
| 否定（`~` プレフィックス） | 除外 | `"~unknown"` |
| 組み合わせ | 種別指定 + 除外 | `"file ~unknown ~unresolved"` |

kindstring はエンティティの Kind 名に対する**部分一致**フィルタ。`"function"` は `Web Javascript Function`、`Web Javascript Unnamed Function`、`Web Javascript Public Method Function` 等すべてにマッチする。

### A.3 `entity.refs(refkindstring, entkindstring, unique)` — 参照一覧取得

エンティティに関連する参照（Reference）を取得する。本設計の中核 API。

```java
Reference[] entity.refs(String refkindstring, String entkindstring, boolean unique)
```

| パラメータ | 型 | 説明 |
|-----------|-----|------|
| `refkindstring` | `String` | 参照種別フィルタ。`null` で全種別 |
| `entkindstring` | `String` | 参照先エンティティの種別フィルタ。`null` で全種別 |
| `unique` | `boolean` | `true`: 同一エンティティへの重複参照を除去し、最初の1件のみ返す。`false`: 全件返す |
| 戻り値 | `Reference[]` | 条件に一致する参照の配列 |

**本設計での使用パターン**:

| 呼び出し | refkindstring | entkindstring | unique | 意味 |
|---------|---------------|---------------|--------|------|
| `jsFile.refs("define", "function", true)` | `define` | `function` | `true` | ファイルが定義している関数（重複除去） |
| `func.refs("call", "function", true)` | `call` | `function` | `true` | 関数が呼び出している関数 |
| `func.refs("callby", "function", true)` | `callby` | `function` | `true` | 関数を呼び出している関数（逆方向） |
| `func.refs("definein", "file", true)` | `definein` | `file` | `true` | 関数が定義されているファイル（逆引き） |
| `cls.refs("definein", null, true)` | `definein` | `null`（全種別） | `true` | クラスの定義位置 |

### A.4 `entity.filerefs(refkindstring, entkindstring, unique)` — ファイル内参照取得

ファイルエンティティ内の全参照を取得する。`refs()` がエンティティ自身の参照を返すのに対し、`filerefs()` はファイル内に出現する**すべての参照**を返す。

```java
Reference[] entity.filerefs(String refkindstring, String entkindstring, boolean unique)
```

パラメータは `refs()` と同じ。**ファイルエンティティ以外に対して呼ぶと空配列を返す。**

**`refs()` と `filerefs()` の違い**:

| メソッド | 対象 | 返す参照 |
|---------|------|---------|
| `refs()` | 任意のエンティティ | そのエンティティ自身に関連する参照のみ |
| `filerefs()` | ファイルエンティティ | ファイル内に出現するすべての参照（他のエンティティの参照も含む） |

本設計では `filerefs()` を events.csv のステップ A（`.on()` 呼び出し位置の特定）で使用する。ファイル全体から `.on()` メソッドの呼び出し参照を横断的に検索するため。

### A.5 `entity.depends()` / `entity.dependsby()` — 依存関係取得

エンティティ間の集約的な依存関係を取得する。

```java
Map<Entity, Reference[]> entity.depends()    // このエンティティが依存している先
Map<Entity, Reference[]> entity.dependsby()  // このエンティティに依存しているもの
```

| 戻り値の要素 | 説明 |
|-------------|------|
| Key（`Entity`） | 依存先（または依存元）のエンティティ |
| Value（`Reference[]`） | その依存関係を構成する個々の参照の配列 |

本設計では HTML → JS の `<script src>` 依存関係の取得に使用する。

### A.6 `entity.lexer(lookupEnts, showInactive, expandMacros)` — 字句解析器

ファイルエンティティから字句解析器（Lexer）を生成する。

```java
Lexer entity.lexer(boolean lookupEnts, boolean showInactive, boolean expandMacros)
```

| パラメータ | 型 | 説明 |
|-----------|-----|------|
| `lookupEnts` | `boolean` | `true`: 各 Lexeme に対応する Entity を関連付ける（`lex.entity()` が有効になる） |
| `showInactive` | `boolean` | `true`: 非アクティブコード（`#ifdef` 除外部分）を含める。**JS では通常 `false`** |
| `expandMacros` | `boolean` | `true`: マクロを展開する。**JS では通常 `false`** |
| 戻り値 | `Lexer` | 字句解析器オブジェクト |

**ファイルエンティティ以外に対して呼ぶと例外がスローされる。**

### A.7 Lexeme の主要メソッド

Lexeme はソースコードのトークン（字句）を表す。双方向連結リストを形成している。

| メソッド | 戻り値 | 説明 |
|---------|-------|------|
| `text()` | `String` | トークンのテキスト内容（ソースコード上の文字列そのもの） |
| `token()` | `String` | トークンの種別名（後述の一覧参照） |
| `entity()` | `Entity` | 関連する Entity。`lexer()` の `lookupEnts=true` で生成した場合のみ有効。対応なしの場合は `null` |
| `reference()` | `Reference` | 関連する Reference。`entity()` と同様に `lookupEnts=true` が必要。対応なしの場合は `null` |
| `next()` | `Lexeme` | 次の Lexeme。ファイル末尾なら `null` |
| `previous()` | `Lexeme` | 前の Lexeme。ファイル先頭なら `null` |
| `lineBegin()` | `int` | トークンの開始行番号 |
| `columnBegin()` | `int` | トークンの開始列番号 |

### A.8 Entity の主要メソッド

| メソッド | 戻り値 | 説明 |
|---------|-------|------|
| `name()` | `String` | 短縮名（例: `"save"`, `"taskManager.js"`） |
| `longname()` | `String` | 完全修飾名（例: `"TaskManager.save"`, `"js/taskManager.js"`） |
| `kind()` | `Kind` | エンティティの種別オブジェクト |
| `type()` | `String` | 型情報（メソッドの戻り値型など。JS では `null` の場合が多い） |

`kind().name()` で種別名の文字列を取得できる（例: `"Web Javascript Function"`）。

### A.9 Reference の主要メソッド

| メソッド | 戻り値 | 説明 |
|---------|-------|------|
| `ent()` | `Entity` | 参照先エンティティ |
| `file()` | `Entity` | 参照が記述されているファイルの Entity |
| `scope()` | `Entity` | 参照が発生しているスコープ（関数やクラス等）の Entity |
| `line()` | `int` | 参照が発生している行番号 |
| `column()` | `int` | 参照が発生している列番号 |
| `kind()` | `Kind` | 参照の種別オブジェクト。`kind().name()` で名前取得 |

---

## 付録 B: JavaScript（Web）向け kindstring リファレンス

Understand では JavaScript は **Web 言語ファミリー** に属し、kindstring のプレフィックスは `Web Javascript` となる。ただし `refs()` や `ents()` のフィルタでは**部分一致**のため、`"function"` と書けば `Web Javascript Function` 等すべてにマッチする。

> **出典**: `C:\Program Files\SciTools\doc\manuals\python\kinds.html`（Web Entity Kinds / Web Reference Kinds セクション）

### B.1 Entity kindstring（エンティティ種別）

#### ファイル

| kindstring | 説明 |
|-----------|------|
| `Web File` | Web ファイル（HTML, CSS, JS 等） |
| `Web Javascript Unresolved File` | 参照されているが見つからない JS ファイル |
| `Web Unknown File` | 種別不明のファイル |

#### 関数

| kindstring | 説明 |
|-----------|------|
| `Web Javascript Function` | 名前付き関数宣言・関数式 |
| `Web Javascript Unnamed Function` | 無名関数 |
| `Web Javascript Unnamed Global Function` | グローバルスコープの無名関数 |
| `Web Javascript Unresolved Function` | 参照されているが解決できない関数 |
| `Web Javascript Public Method Function` | パブリックメソッド |
| `Web Javascript Public Method Function Static` | パブリック静的メソッド |
| `Web Javascript Public Method Function Instance` | パブリックインスタンスメソッド |
| `Web Javascript Protected Method Function` | プロテクトメソッド |
| `Web Javascript Private Method Function` | プライベートメソッド |

#### クラス

| kindstring | 説明 |
|-----------|------|
| `Web Javascript Class` | ES6 クラス宣言 |
| `Web Javascript Function Class` | コンストラクタ関数（pre-ES6 パターン） |
| `Web Javascript Unresolved Class` | 参照されているが解決できないクラス |

#### 変数・プロパティ

| kindstring | 説明 |
|-----------|------|
| `Web Javascript Variable Global` | グローバルスコープの変数 |
| `Web Javascript Variable Local` | ローカルスコープの変数 |
| `Web Javascript Parameter` | 関数パラメータ |
| `Web Javascript Public Property` | パブリックプロパティ |
| `Web Javascript Private Property` | プライベートプロパティ |
| `Web Javascript Unresolved Property` | 解決できないプロパティ |

#### jQuery 固有

| kindstring | 説明 |
|-----------|------|
| `Web Javascript JQuery Selector` | jQuery セレクタ式（**`.on()` 解析で重要**） |

#### その他

| kindstring | 説明 |
|-----------|------|
| `Web Javascript Predefined Object` | 組み込みオブジェクト（`Math`, `JSON`, `console` 等） |
| `Web Javascript Namespace` | 名前空間（TypeScript） |
| `Web Javascript Interface` | インターフェース（TypeScript） |
| `Web Javascript Enum` | 列挙型（TypeScript） |

### B.2 Reference kindstring（参照種別）

#### 定義系

| kindstring | 逆方向 | 説明 |
|-----------|--------|------|
| `Web Javascript Define` | `Definein` | 標準的な定義 |
| `Web Javascript Define Implicit` | `Definein` | 暗黙的な定義（解析器が推測） |
| `Web Javascript Prototype Define Implicit` | `Definein` | プロトタイプ上の定義（`Foo.prototype.bar = ...`） |
| `Web Javascript This Define Implicit` | `Definein` | `this` 経由の定義（`this.prop = ...`） |
| `Web Javascript Define Export` | `Definein` | `export` 付き定義 |

#### 呼び出し系

| kindstring | 逆方向 | 説明 |
|-----------|--------|------|
| `Web Javascript Call` | `Callby` | 直接的な関数/メソッド呼び出し |
| `Web Javascript Call Possible` | `Callby` | 呼び出しの可能性がある参照（動的解決） |
| `Web Javascript Call Implicit` | `Callby` | 暗黙的な呼び出し（コンストラクタ、コールバック等） |
| `Web Javascript Call New` | `Callby` | `new` 式（コンストラクタ呼び出し） |

#### 使用系

| kindstring | 逆方向 | 説明 |
|-----------|--------|------|
| `Web Javascript Use` | `Useby` | 変数/プロパティの読み取り |
| `Web Javascript Use Ptr` | `Useby` | コールバック渡し等の参照使用 |
| `Web Javascript Set` | `Setby` | 変数/プロパティへの代入 |
| `Web Javascript Set Init` | `Setby` | 初期化代入（最初の代入） |
| `Web Javascript Modify` | `Modifyby` | 値の変更（`x++`, `x += 1` 等） |

#### インポート・継承系

| kindstring | 逆方向 | 説明 |
|-----------|--------|------|
| `Web Javascript Import` | `Importby` | ES6 import 文 |
| `Web Javascript Import From` | `Importby` | `import { x } from 'module'` |
| `Web Javascript Require` | `Requireby` | CommonJS `require()` |
| `Web Javascript Extend` | `Extendby` | クラス継承（`extends`） |
| `Web Javascript Overrides` | `Overriddenby` | メソッドオーバーライド |

#### HTML 固有（`<script src>` 関連）

| kindstring | 逆方向 | 説明 |
|-----------|--------|------|
| `Web Html Src` | `Srcby` | **`<script src>` や `<img src>` 等の src 属性参照** |
| `Web Html Call` | `Callby` | HTML からの関数呼び出し（`onclick="func()"` 等） |
| `Web Html Link` | `Linkby` | `<link href>` 等のリンク参照 |
| `Web Html Define` | `Definein` | HTML 内の定義 |

### B.3 `Lexeme.token()` が返すトークン種別（JavaScript）

| `token()` の値 | 意味 | 例 |
|---------------|------|-----|
| `Keyword` | 予約語 | `function`, `var`, `if`, `return` |
| `Identifier` | 識別子 | `TaskManager`, `save`, `init` |
| `Literal` | 数値リテラル | `42`, `3.14` |
| `String` | 文字列リテラル | `'click'`, `".btn-save"` |
| `Operator` | 演算子 | `+`, `=`, `===`, `.` |
| `Punctuation` | 区切り文字 | `{`, `}`, `(`, `)`, `;`, `,` |
| `Comment` | コメント | `// comment`, `/* ... */` |
| `Whitespace` | 空白 | スペース、タブ |
| `Newline` | 改行 | 改行文字 |

---

## 付録 C: 設計上の補足事項

### C.1 `Web Javascript JQuery Selector` Entity の活用可能性

Understand は jQuery セレクタ式を `Web Javascript JQuery Selector` エンティティとして認識する。
これにより、events.csv のセレクタ抽出において、Lexer でのトークン走査に加えて、`db.ents("JQuery Selector")` で全セレクタを列挙できる可能性がある。

```java
// jQuery セレクタの全一覧を取得
Entity[] selectors = db.ents("JQuery Selector");
for (Entity sel : selectors) {
    System.out.println(sel.name());  // ".btn-save", "#task-list" 等
}
```

> **要検証**: セレクタ Entity と `.on()` 呼び出しの Reference がどう紐づくかは、実際の UDB で確認が必要。セレクタ Entity から `refs("callby")` で `.on()` への参照が取れれば、Lexer を使わないアプローチが可能。

### C.2 `Web Html Src` Reference の活用可能性

`<script src="...">` は `Web Html Src` 参照として記録される可能性がある。これが使えれば、`depends()` ではなく `refs("src", "file", true)` で HTML からインポートされた JS ファイルを正確に取得できる。

```java
// HTML ファイルの <script src> 参照を取得
Reference[] srcRefs = htmlFile.refs("src", "file", true);
for (Reference ref : srcRefs) {
    Entity jsFile = ref.ent();
    System.out.println(jsFile.name());  // "taskManager.js" 等
}
```

### C.3 無名関数の Entity 名

Understand は無名関数を `Web Javascript Unnamed Function` エンティティとして認識する。
`name()` がどのような値を返すか（`"(anonymous)"`, `"(unnamed)"`, 空文字等）は要検証だが、Entity として存在するため `refs("call", "function", true)` で呼び出し先を取得できる可能性が高い。

これにより、events.csv の「ハンドラ内呼び出し」取得が、Lexer フォールバック（セクション 8.2）ではなく `refs()` ベースで実装できる可能性がある。

### C.4 本設計の「要検証」項目まとめ

以下は実際の UDB で動作確認が必要な項目の一覧。

| # | 項目 | 確認方法 | 影響するセクション |
|---|------|---------|------------------|
| 1 | `htmlFile.depends()` が `<script src>` の JS ファイルを返すか | HTML + JS プロジェクトの UDB で `depends()` を呼ぶ | 3.1, 6.3 |
| 2 | `Web Html Src` 参照で `<script src>` を取得できるか | `htmlFile.refs("src", "file", true)` を試す | 付録 C.2 |
| 3 | JS 関数の `longname()` のフォーマット（ファイルパスプレフィックスの有無） | 任意の JS 関数で `longname()` を出力 | 3.3, 6.4 |
| 4 | 無名関数（`.on()` ハンドラ）の Entity 名 | `db.ents("Unnamed Function")` で名前を確認 | 4.2, 付録 C.3 |
| 5 | 無名関数の `refs("call", "function", true)` でハンドラ内呼び出しが取れるか | `.on()` を含む JS で試す | 5.2 ステップ D |
| 6 | `Web Javascript JQuery Selector` Entity がセレクタ文字列を `name()` で返すか | `db.ents("JQuery Selector")` で確認 | 付録 C.1 |
| 7 | Understand が `.on()` メソッドをどのような参照種別で記録するか | `.on()` を含む JS で `filerefs("call")` を確認 | 5.2 ステップ A |
