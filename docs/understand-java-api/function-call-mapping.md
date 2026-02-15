# Entity と Reference による関数呼び出しの紐づけ

Understand Java API では、Entity（エンティティ）と Reference（参照）を組み合わせることで、関数（メソッド）の呼び出し関係を自在に取得できます。
本ドキュメントでは、「ある関数の呼び出し元は何か？」「呼び出しチェーンはどうなっているか？」といった疑問に対する**考え方**を段階的に解説します。

### 前提知識

以下のドキュメントの内容を前提とします。

- [02 - 主要概念](02-core-concepts.md) — Entity, Reference, Kind の基本
- [03 - コード探索](03-code-exploration.md) — `refs()` を使った呼び出し関係の取得

### このドキュメントを読むとできること

- ある関数の**呼び出し元関数名**を取得できる
- 呼び出しチェーン（A → B → C）を**再帰的にたどれる**
- 関数変更の**影響範囲**を分析できる

### 使用する具体例

本ドキュメントでは、SampleProject.java の以下のクラス・メソッドを一貫して例に使用します。

```
SampleProject.main()
    ├── TaskManager.addTask()
    ├── TaskManager.completeTask()
    ├── TaskManager.printAll()
    └── TaskManager.countCompleted()
```

---

## 基本の考え方：直接呼び出しの紐づけ

関数の呼び出し関係は、Reference オブジェクトに集約されています。1つの Reference は「**誰が**、**誰を**、**どこで**呼んだか」という情報を持ちます。

### Reference の構造

```
┌─────────────────────────────────────────┐
│            Reference オブジェクト          │
│                                         │
│  scope() ──→ 呼び出し元 Entity (誰が)     │
│  ent()   ──→ 呼び出し先 Entity (誰を)     │
│  file()  ──→ ファイル Entity  (どこで)     │
│  line()  ──→ 行番号                      │
│  kind()  ──→ 参照の種類 ("call" 等)       │
└─────────────────────────────────────────┘
```

> **ポイント:** `scope()` が呼び出し**元**、`ent()` が呼び出し**先**です。この関係を覚えておくことが、以降のすべてのパターンの基礎になります。

### 具体例：completeTask() → findById()

SampleProject.java の `completeTask()` メソッド（110行目）には `Task task = findById(id);` というコードがあります。この呼び出し関係を Reference で表すと：

```
completeTask() ──call──→ findById()

この Reference の各メソッドが返す値:
  ref.scope().name() → "completeTask"     （呼び出し元の関数名）
  ref.ent().name()   → "findById"         （呼び出し先の関数名）
  ref.file().name()  → "SampleProject.java"
  ref.line()         → 110
  ref.kind().name()  → "Call"
```

### 双方向アクセス：call と callby

同じ呼び出し関係を、**呼ぶ側**からも**呼ばれる側**からも取得できます。

```
【順方向】completeTask 視点で「自分が呼ぶ関数」を取得
  completeTask.refs("call", "method", true)
    → findById への Reference が返る
       ref.scope() = completeTask  (自分自身)
       ref.ent()   = findById      (呼び出し先)

【逆方向】findById 視点で「自分を呼ぶ関数」を取得
  findById.refs("callby", "method", true)
    → completeTask からの Reference が返る
       ref.scope() = findById      (自分自身)
       ref.ent()   = completeTask  (呼び出し元)
```

| 参照種別 | 方向 | 取得できるもの | scope() | ent() |
|---------|------|-------------|---------|-------|
| `call` | 順方向 | このメソッドが呼ぶメソッド | 自分自身 | 呼び出し先 |
| `callby` | 逆方向 | このメソッドを呼ぶメソッド | 自分自身 | 呼び出し元 |

> **対称性:** `call` と `callby` は同じ関係を異なる視点から見たものです。A.refs("call") に B が含まれるなら、B.refs("callby") に必ず A が含まれます。

### refs() の第3引数 unique について

`refs()` の第3引数 `unique` は重複制御です。

- `true` — 同じ関数を複数回呼んでいても Reference は1つだけ返る（関数の紐づけ向き）
- `false` — 呼び出しごとに Reference が返る（呼び出し箇所の列挙向き）

関数の呼び出し関係を紐づける用途では、通常 `true` を指定します。

---

## 呼び出し元関数名を取得する

「ある関数を呼んでいるのは誰か？」——これが最も基本的な問いです。`refs("callby")` で逆方向の参照を取得し、`ent()` で呼び出し元の関数名を取得できます。

### 思考プロセス

**問い:** 「`findById` を呼んでいる関数は何か？」

```
Step 1: findById の Entity を取得する
  db.ents("method") でメソッド一覧を取得し、findById を探す

Step 2: callby で逆方向の参照を取得する
  findById.refs("callby", "method", true) → Reference[]

Step 3: 各 Reference の ent() で呼び出し元 Entity を得る
  ref.ent() → completeTask の Entity

Step 4: 呼び出し元の名前を取得する
  ref.ent().name() → "completeTask"
```

### コード例

```java
// findById を呼び出している関数を一覧表示
Entity[] methods = db.ents("method");
for (Entity method : methods) {
    if (method.name().equals("findById")) {
        Reference[] callers = method.refs("callby", "method", true);
        System.out.println("=== " + method.longname() + " の呼び出し元 ===");
        for (Reference ref : callers) {
            System.out.printf("  ← %s (ファイル: %s, 行: %d)%n",
                ref.ent().longname(),
                ref.file().name(),
                ref.line());
        }
    }
}
```

### 出力例

```
=== sample.TaskManager.findById の呼び出し元 ===
  ← sample.TaskManager.completeTask (ファイル: SampleProject.java, 行: 110)
```

### name() と longname() の使い分け

呼び出し元の名前は用途に応じて使い分けます。

| メソッド | 返す値の例 | 用途 |
|---------|-----------|------|
| `name()` | `"completeTask"` | 表示用（短い） |
| `longname()` | `"sample.TaskManager.completeTask"` | 一意な識別が必要な場合 |

同名メソッドが複数クラスに存在する場合は `longname()` を使わないと区別できません。

### 呼び出し元の所属クラスを取得する

呼び出し元関数がどのクラスに属しているかも取得できます。`definein` を使って逆引きします。

```java
// 呼び出し元関数の所属クラスを取得
Entity caller = ref.ent();  // callby の場合、ent() が呼び出し元
Reference[] classRefs = caller.refs("definein", "class", true);
if (classRefs.length > 0) {
    String className = classRefs[0].ent().name();
    System.out.println("所属クラス: " + className);  // → "TaskManager"
}
```

> **考え方:** `definein` は「この Entity はどこで定義されているか」を返す参照種別です。メソッドの Entity に対して `refs("definein", "class", true)` を呼ぶと、そのメソッドが定義されているクラスの Entity を取得できます。

---

## 呼び出しチェーンの探索

直接的な呼び出し関係だけでなく、A → B → C → ... と続く**呼び出しチェーン**をたどりたい場面があります。`refs("call")` を再帰的に呼び出すことで実現できます。

### SampleProject.java の呼び出しチェーン全体像

```
main()
  ├──→ addTask()
  │      └──→ isValid()
  │             └──→ getName()
  ├──→ completeTask()
  │      ├──→ findById()
  │      │      └──→ getId()
  │      └──→ complete()
  ├──→ printAll()
  │      └──→ toDisplayString()
  └──→ countCompleted()
         └──→ isCompleted()
```

### 考え方：1段ずつたどる

呼び出しチェーンは、`refs("call")` を段階的に繰り返すことで追跡できます。

```
起点: main() の Entity

1段目: main().refs("call", "method", true)
  → [addTask, completeTask, printAll, countCompleted]

2段目: completeTask().refs("call", "method", true)
  → [findById, complete]

3段目: findById().refs("call", "method", true)
  → [getId]

4段目: getId().refs("call", "method", true)
  → []  （末端 — これ以上呼び出しはない）
```

この「1段目の結果に対して再度 refs("call") を呼ぶ」操作を繰り返すのが再帰探索です。

### 再帰探索のコード例

```java
/**
 * 指定メソッドから始まる呼び出しチェーンを表示する。
 * @param method 起点メソッドの Entity
 * @param depth  現在の深さ（インデント用）
 * @param visited 訪問済みセット（サイクル防止）
 */
private static void printCallChain(Entity method, int depth,
                                    Set<String> visited) {
    String indent = "  ".repeat(depth);
    System.out.println(indent + method.longname());

    // サイクル防止: uniquename で訪問済みチェック
    if (visited.contains(method.uniquename())) {
        System.out.println(indent + "  (循環参照 - 省略)");
        return;
    }
    visited.add(method.uniquename());

    // 呼び出し先を再帰的にたどる
    Reference[] callRefs = method.refs("call", "method", true);
    for (Reference ref : callRefs) {
        printCallChain(ref.ent(), depth + 1, visited);
    }
}
```

呼び出し方:

```java
Entity[] methods = db.ents("method");
for (Entity method : methods) {
    if (method.name().equals("main")) {
        printCallChain(method, 0, new java.util.HashSet<>());
    }
}
```

### 出力例

```
sample.SampleProject.main
  sample.TaskManager.addTask
    sample.Task.isValid
      sample.BaseItem.getName
  sample.TaskManager.completeTask
    sample.TaskManager.findById
      sample.BaseItem.getId
    sample.Task.complete
  sample.TaskManager.printAll
    sample.Task.toDisplayString
  sample.TaskManager.countCompleted
    sample.Task.isCompleted
```

### サイクル防止が必要な理由

相互再帰（A が B を呼び、B が A を呼ぶ）が存在する場合、サイクル防止なしでは無限ループになります。

```
// 相互再帰の例（SampleProject.java にはないが、一般的に起こりうる）
methodA() → methodB() → methodA() → methodB() → ...（無限ループ）
```

`uniquename()` を訪問済みキーに使う理由:
- `name()` だとオーバーロードされた同名メソッドを区別できない
- `uniquename()` はデータベース内で一意な識別子を返す

### 深さ制限

大規模なコードベースでは、呼び出しチェーンが非常に深くなる場合があります。`maxDepth` パラメータで探索範囲を制限できます。

```java
private static void printCallChain(Entity method, int depth,
                                    Set<String> visited, int maxDepth) {
    if (depth > maxDepth) {
        System.out.println("  ".repeat(depth) + "... (深さ上限)");
        return;
    }
    // 以降は同じ処理
}
```

---

## 影響分析：callby の再帰探索

「ある関数を変更したら、どこまで影響するか？」という問いに答えるのが影響分析です。呼び出しチェーン探索の**逆方向版**で、`refs("callby")` を再帰的にたどります。

### 考え方

セクション4では `refs("call")` で**下流**（呼び出し先）をたどりました。影響分析では `refs("callby")` で**上流**（呼び出し元）をたどります。

```
【セクション4: 下流探索】
  main() ──→ completeTask() ──→ findById() ──→ getId()
  「main から何が呼ばれるか？」

【セクション5: 上流探索（影響分析）】
  main() ←── completeTask() ←── findById()
  「findById を変えたらどこに影響するか？」
```

### 具体例

**問い:** 「`findById()` を変更したら、どの関数に影響があるか？」

```
findById()
  ↑ callby
completeTask()     ← findById を直接呼んでいる → 影響あり
  ↑ callby
main()             ← completeTask を呼んでいる → 間接的に影響あり
```

### コード例

```java
/**
 * 指定メソッドの影響範囲を表示する。
 * @param method 起点メソッドの Entity
 * @param depth  現在の深さ（インデント用）
 * @param visited 訪問済みセット（サイクル防止）
 */
private static void printImpactScope(Entity method, int depth,
                                      Set<String> visited) {
    String indent = "  ".repeat(depth);
    System.out.println(indent + method.longname());

    if (visited.contains(method.uniquename())) {
        System.out.println(indent + "  (循環参照 - 省略)");
        return;
    }
    visited.add(method.uniquename());

    // callby で「この関数を呼んでいる関数」を再帰的にたどる
    Reference[] callerRefs = method.refs("callby", "method", true);
    for (Reference ref : callerRefs) {
        printImpactScope(ref.ent(), depth + 1, visited);
    }
}
```

呼び出し方:

```java
Entity[] methods = db.ents("method");
for (Entity method : methods) {
    if (method.name().equals("findById")) {
        System.out.println("=== findById の影響範囲 ===");
        printImpactScope(method, 0, new java.util.HashSet<>());
    }
}
```

### 出力例

```
=== findById の影響範囲 ===
sample.TaskManager.findById
  sample.TaskManager.completeTask
    sample.SampleProject.main
```

この結果から、`findById()` を変更した場合に `completeTask()` と `main()` が影響を受ける可能性があることがわかります。

### 実務での活用

- **リファクタリング前の影響調査** — 変更対象メソッドの影響範囲を事前に把握し、修正漏れを防ぐ
- **テスト範囲の特定** — 影響を受ける関数に対応するテストケースを優先的に実行する

---

## まとめ

### パターン早見表

| やりたいこと | API の使い方 | kindstring |
|---|---|---|
| この関数が呼ぶ関数一覧 | `method.refs("call", "method", true)` | `call` |
| この関数を呼ぶ関数一覧 | `method.refs("callby", "method", true)` | `callby` |
| 呼び出し元の関数名 | `ref.ent().longname()`（callby の場合） | — |
| 呼び出し先の関数名 | `ref.ent().longname()`（call の場合） | — |
| 呼び出し元の所属クラス | `caller.refs("definein", "class", true)` | `definein` |
| 呼び出しチェーン（下流） | `refs("call")` を再帰的にたどる | `call` |
| 影響分析（上流） | `refs("callby")` を再帰的にたどる | `callby` |

### 関連ドキュメント

- [02 - 主要概念](02-core-concepts.md) — Entity, Reference の基本概念と「参照の方向」
- [03 - コード探索](03-code-exploration.md) — ユースケース3「メソッド呼び出し関係」での実践例
- [04 - 依存関係分析](04-dependency-analysis.md) — CSV 出力による呼び出し関係の一括エクスポート
- [05 - API リファレンス](05-api-reference.md) — `Entity.refs()`, `Reference.scope()`, `Reference.ent()` 等の API 詳細
