# 02 - 主要概念（Core Concepts）

Understand Java API を効果的に活用するには、API が提供するオブジェクトモデルの全体像を理解することが重要です。
本章では、Database、Entity（エンティティ）、Reference（参照）、Kind（種別）、Lexer/Lexeme（字句解析器/字句）の各概念を体系的に解説します。

---

## オブジェクトモデル概念図

Understand Java API のオブジェクトモデルは、以下の階層構造を持ちます。

```
Understand.open(path)
    └── Database
         ├── ents(kindstring) → Entity[]
         ├── lookup_uniquename(name) → Entity
         └── close()

Entity
    ├── kind() → Kind
    ├── refs(refkind, entkind, unique) → Reference[]
    ├── ents(refkind, entkind) → Entity[]
    ├── depends() → Map<Entity, Reference[]>
    ├── dependsby() → Map<Entity, Reference[]>
    └── lexer(lookupEnts, showInactive, expandMacros) → Lexer
         └── first() → Lexeme → next() → Lexeme → ...

Reference
    ├── ent() → Entity (参照先)
    ├── scope() → Entity (参照元)
    ├── file() → Entity (ファイル)
    ├── kind() → Kind
    ├── line() → int
    └── column() → int
```

### API の基本的な流れ

1. **`Understand.open(path)`** がエントリーポイントです。`.udb` ファイルのパスを渡すと `Database` オブジェクトが返されます。
2. **`Database`** はソースコードの解析結果全体を保持します。`ents()` でエンティティの一覧を取得したり、`lookup_uniquename()` で特定のエンティティを検索したりできます。
3. **`Entity`** はソースコード中のクラスやメソッドなどの個々の要素を表します。Entity から `refs()` で参照情報を、`kind()` で種別を、`lexer()` で字句解析器を取得できます。
4. **`Reference`** はエンティティ間の関係（呼び出し、定義、使用など）を表します。参照先・参照元・発生位置などの情報を持ちます。
5. **`Kind`** はエンティティや参照の分類を表します。フィルタリングに使用します。
6. **`Lexer` / `Lexeme`** はソースファイルをトークン単位で解析するための仕組みです。

---

## Entity（エンティティ）

Entity（エンティティ）とは、ソースコード上の**名前付き要素**を表すオブジェクトです。
クラス、メソッド、変数、ファイル、パッケージなど、Understand がソースコードを解析して認識したすべての構成要素が Entity として表現されます。

### Entity の主要メソッド

| メソッド | 説明 | 例 |
|---------|------|-----|
| `name()` | 短い名前 | `Task` |
| `longname()` | 完全修飾名 | `sample.Task` |
| `uniquename()` | 一意な名前（DB 内で一意） | (DB 固有の値) |
| `kind()` | エンティティの種別 | `Java Class` |
| `type()` | データ型（変数等） | `String` |

#### 名前の使い分け

- **`name()`** は表示用に便利ですが、同名のクラスやメソッドが複数ある場合に区別できません。
- **`longname()`** はパッケージ名を含む完全修飾名で、通常は一意ですがオーバーロードされたメソッドでは同一になることがあります。
- **`uniquename()`** はデータベース内で必ず一意になる識別子です。`Database.lookup_uniquename()` で Entity を再取得する際に使用します。

### Entity の取得方法

```java
// 全エンティティを取得
Entity[] all = db.ents(null);

// kindstring でフィルタして取得（クラスのみ）
Entity[] classes = db.ents("class");

// 一意な名前で検索
Entity ent = db.lookup_uniquename(uniqueName);
```

### Entity の無効化に関する注意

Entity オブジェクトは、取得元の Database が `close()` された時点で**無効**になります。
閉じた後の Entity に対してメソッドを呼び出すと、予期しない動作やエラーの原因になります。
Entity を長期間保持する必要がある場合は、`uniquename()` の値を文字列として保存し、次回データベースを開いた際に `lookup_uniquename()` で再取得してください。

---

## Reference（参照）

Reference（参照）とは、**エンティティ間の関係**を表すオブジェクトです。
「メソッド A がメソッド B を呼び出している」「クラス C がインターフェース D を実装している」といった関係が、それぞれ Reference として記録されます。

### Reference の構造

Reference は以下の情報を持ちます。

| メソッド | 説明 |
|---------|------|
| `ent()` | 参照先の Entity（ターゲット） |
| `scope()` | 参照元の Entity（ソース） |
| `file()` | 参照が発生しているファイルの Entity |
| `kind()` | 参照の種別（Kind） |
| `line()` | 参照が発生している行番号 |
| `column()` | 参照が発生している列番号 |

### 参照の方向

Reference を正しく理解するうえで最も重要なのは**方向**の概念です。

「メソッド A がメソッド B を呼び出している」という関係では：

- `scope()` = A（呼び出し元 = 参照の起点）
- `ent()` = B（呼び出し先 = 参照の対象）

以下の図は、`TaskManager.completeTask` が `TaskManager.findById` を呼び出している参照を表しています。

```
scope (呼び出し元)                    ent (呼び出し先)
┌─────────────┐    call (参照)    ┌──────────────┐
│ completeTask │ ─────────────→ │   findById   │
└─────────────┘                  └──────────────┘
      file: SampleProject.java, line: 110, column: 21
```

この参照の各メソッドは以下の値を返します。

| メソッド | 戻り値 |
|---------|-------|
| `scope()` | `completeTask` の Entity |
| `ent()` | `findById` の Entity |
| `file()` | `SampleProject.java` の Entity |
| `kind().name()` | `Java Call` |
| `line()` | `110` |
| `column()` | `21` |

### 参照の取得方法

```java
// Entity からその Entity が参照しているものを取得
Reference[] refs = entity.refs("call", null, true);

// 各参照の情報を表示
for (Reference ref : refs) {
    System.out.printf("%s が %s を呼び出し (file: %s, line: %d)%n",
        ref.scope().name(),
        ref.ent().name(),
        ref.file().name(),
        ref.line());
}
```

`refs()` メソッドの引数：

| 引数 | 説明 |
|------|------|
| `refkind` | 参照の種別フィルタ（kindstring）。`null` で全種別 |
| `entkind` | 参照先エンティティの種別フィルタ。`null` で全種別 |
| `unique` | `true` にすると同一エンティティへの重複参照を除外 |

---

## Kind（種別）

Kind（種別）とは、Entity や Reference の**分類**を表すオブジェクトです。
Entity がクラスなのかメソッドなのか、Reference が呼び出しなのか定義なのかを判別するために使用します。

### kindstring フィルタ構文

`db.ents()` や `entity.refs()` に渡す kindstring（種別フィルタ文字列）には、以下の構文が使えます。

| 構文 | 説明 | 例 |
|------|------|-----|
| 単一指定 | 指定した種別のみ | `"class"` |
| 複数指定（カンマ区切り） | いずれかに該当 | `"class, interface"` |
| 否定（`~` プレフィックス） | 指定した種別を除外 | `"~unknown"` |
| 組み合わせ | 種別指定と除外を同時に | `"class ~unknown ~unresolved"` |

> **ポイント:** `~unknown` と `~unresolved` は頻繁に使います。
> Understand が解析できなかった外部ライブラリのクラスなどが `Unknown` や `Unresolved` として登録されるため、
> これらを除外することで解析対象のソースコードに定義されたエンティティのみを取得できます。

### Java 向け Entity kindstring 一覧

| kindstring | 対象 |
|-----------|------|
| `class` | クラス |
| `interface` | インターフェース |
| `enum` | 列挙型 |
| `method` | メソッド |
| `variable` | 変数（フィールド、ローカル変数、パラメータ含む） |
| `parameter` | メソッドパラメータ |
| `file` | ソースファイル |
| `package` | パッケージ |
| `~unknown` | 未解決を除外 |
| `~unresolved` | 未解決を除外 |

使用例：

```java
// クラスとインターフェースを取得（未解決を除外）
Entity[] types = db.ents("class, interface ~unknown ~unresolved");

// メソッドのみ取得
Entity[] methods = db.ents("method");

// 変数のみ取得（フィールド、ローカル変数、パラメータすべて含む）
Entity[] variables = db.ents("variable");
```

### Java 向け Reference kindstring 一覧

Reference の kindstring には**方向**の概念があります。
例えば `call` は「呼び出す側」から見た参照で、`callby` は「呼び出される側」から見た逆方向の参照です。

| kindstring | 意味 | 逆方向 |
|-----------|------|-------|
| `call` | メソッド呼び出し | `callby` |
| `use` | 変数の使用 | `useby` |
| `set` | 変数への代入 | `setby` |
| `define` | 定義 | `definein` |
| `declare` | 宣言 | `declarein` |
| `extend` | クラス継承 | `extendby` |
| `implement` | インターフェース実装 | `implementby` |
| `import` | インポート | `importby` |
| `create` | オブジェクト生成 | `createby` |
| `typed` | 型の使用 | `typedby` |
| `override` | メソッドオーバーライド | `overrideby` |
| `couple` | 結合 | `coupleby` |

使用例：

```java
// メソッドの呼び出し先を取得
Reference[] calls = method.refs("call", null, true);

// このクラスを継承しているクラスを取得（逆方向）
Reference[] subclasses = cls.refs("extendby", "class", true);

// このインターフェースを実装しているクラスを取得（逆方向）
Reference[] implementors = iface.refs("implementby", "class", true);
```

---

## Lexer / Lexeme（字句解析）

Lexer（字句解析器）は、ソースファイルを**トークン単位**で解析するための仕組みです。
Entity の `lexer()` メソッドで取得し、Lexeme（字句）の連結リストとしてソースコードの各トークンにアクセスできます。

### Lexer の取得

```java
// ファイルエンティティから Lexer を取得
Lexer lexer = fileEntity.lexer(true, false, false);
```

`Entity.lexer()` の引数：

| 引数 | 型 | 説明 |
|------|-----|------|
| `lookupEnts` | `boolean` | `true` にすると各 Lexeme に対応する Entity を関連付ける |
| `showInactive` | `boolean` | `true` にすると非アクティブコード（`#ifdef` で除外された部分等）も含める |
| `expandMacros` | `boolean` | `true` にするとマクロを展開する |

> **注意:** Java では `showInactive` と `expandMacros` は主に C/C++ 向けの機能です。
> Java で使用する場合は通常 `false` を指定します。

### Lexeme の連結リスト構造

Lexer から取得した Lexeme は**双方向連結リスト**を形成します。

```
first() → Lexeme → next() → Lexeme → next() → Lexeme → ... → null
                    ← previous()      ← previous()
```

- `lexer.first()` で最初の Lexeme を取得します。
- `lexeme.next()` で次の Lexeme に進みます。末尾に到達すると `null` が返ります。
- `lexeme.previous()` で前の Lexeme に戻ります。先頭では `null` が返ります。

### Lexeme の主要メソッド

| メソッド | 説明 |
|---------|------|
| `token()` | トークンの種類を文字列で返す |
| `text()` | トークンのテキスト内容を返す |
| `entity()` | 関連付けられた Entity を返す（`lookupEnts=true` の場合） |
| `lineBegin()` | トークンの開始行番号 |
| `columnBegin()` | トークンの開始列番号 |
| `next()` | 次の Lexeme（末尾では `null`） |
| `previous()` | 前の Lexeme（先頭では `null`） |

### token() の種類

`token()` が返す代表的な値は以下のとおりです。

| token() | 意味 | 例 |
|---------|------|-----|
| `Keyword` | キーワード | `class`, `public`, `if` |
| `Identifier` | 識別子 | `Task`, `main` |
| `Literal` | リテラル | `"hello"`, `42` |
| `String` | 文字列リテラル | `"設計書作成"` |
| `Operator` | 演算子 | `+`, `=`, `==` |
| `Punctuation` | 区切り文字 | `{`, `}`, `;` |
| `Comment` | コメント | `// コメント` |
| `Whitespace` | 空白 | (スペース、タブ) |
| `Newline` | 改行 | (改行文字) |

### 使用例

以下の例は、ファイルエンティティから識別子トークンとその関連エンティティを列挙します。

```java
Entity[] files = db.ents("file");
for (Entity file : files) {
    Lexer lexer = file.lexer(true, false, false);
    Lexeme lexeme = lexer.first();
    while (lexeme != null) {
        if ("Identifier".equals(lexeme.token()) && lexeme.entity() != null) {
            System.out.printf("  %s (Entity: %s, line: %d)%n",
                lexeme.text(),
                lexeme.entity().longname(),
                lexeme.lineBegin());
        }
        lexeme = lexeme.next();
    }
}
```

---

## リソース管理の注意点

Understand Java API は内部でネイティブライブラリ（JNI）を使用しています。
リソースの解放を怠ると、メモリリークやプロセスのハングアップの原因になります。

### 必ず Database.close() を呼ぶ

`Database` オブジェクトは使い終わったら**必ず** `close()` で閉じてください。
例外発生時でも確実にリソースを解放するため、`try-finally` パターンを使用します。

```java
Database db = null;
try {
    db = Understand.open(path);
    // ... 処理 ...
} finally {
    if (db != null) db.close();
}
```

### close() 後のオブジェクト無効化

`Database.close()` を呼び出すと、そのデータベースから取得した**すべての関連オブジェクト**が無効になります。

- Entity
- Reference
- Kind
- Lexer
- Lexeme

閉じた後にこれらのオブジェクトを使用しないでください。

### 同時に開けるデータベースは1つだけ

Understand Java API では、**同時に1つのデータベースしか開くことができません**。
既にデータベースが開いている状態で `Understand.open()` を呼び出すと、`UnderstandException`（`DBAlreadyOpen`）がスローされます。

```java
// NG: 2つ目を開こうとするとエラー
Database db1 = Understand.open("project1.udb");
Database db2 = Understand.open("project2.udb"); // DBAlreadyOpen 例外

// OK: 1つ目を閉じてから2つ目を開く
Database db1 = Understand.open("project1.udb");
// ... db1 の処理 ...
db1.close();
Database db2 = Understand.open("project2.udb");
// ... db2 の処理 ...
db2.close();
```

---

次章: [03 - コード構造の探索（Code Exploration）](03-code-exploration.md)
