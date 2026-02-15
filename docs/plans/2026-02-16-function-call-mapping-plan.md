# Function Call Mapping Guide 実装計画

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Entity と Reference で関数呼び出し関係を紐づける「考え方」を解説する独立ドキュメントを作成する

**Architecture:** 単一 Markdown ファイル（`docs/understand-java-api/function-call-mapping.md`）を6セクションに分けて段階的に執筆。SampleProject.java の具体的な呼び出し関係を一貫して使用する。

**Tech Stack:** Markdown（図解はテキストベース）、Java コード例（擬似コードではなく API 準拠のスニペット）

---

### Task 1: セクション1「はじめに」を執筆

**Files:**
- Create: `docs/understand-java-api/function-call-mapping.md`

**Step 1: ファイルを作成し、セクション1を執筆**

以下の内容を含める:
- ドキュメントのタイトル: `# Entity と Reference による関数呼び出しの紐づけ`
- 目的の説明（1段落）
- 前提知識: 02-core-concepts.md、03-code-exploration.md へのリンク
- このドキュメントを読むとできること（箇条書き3項目）:
  - ある関数の呼び出し元・呼び出し先を取得できる
  - 呼び出しチェーン（A→B→C）を再帰的にたどれる
  - 関数変更の影響範囲を分析できる
- SampleProject.java を例として使用する旨を記載

スタイル準拠:
- 技術用語の初出は日本語併記: Entity（エンティティ）、Reference（参照）
- 見出しレベル: # タイトル、## 大セクション

**Step 2: コミット**

```bash
git add docs/understand-java-api/function-call-mapping.md
git commit -m "docs: add introduction section for function call mapping guide"
```

---

### Task 2: セクション2「基本の考え方：直接呼び出しの紐づけ」を執筆

**Files:**
- Modify: `docs/understand-java-api/function-call-mapping.md`

**Step 1: セクション2を追記**

以下の内容を含める:

1. **Reference の構造図**:
```
┌─────────────────────────────────────────┐
│            Reference オブジェクト          │
│                                         │
│  scope() ──→ 呼び出し元 Entity (誰が)     │
│  ent()   ──→ 呼び出し先 Entity (誰を)     │
│  file()  ──→ ファイル Entity (どこで)      │
│  line()  ──→ 行番号                      │
│  kind()  ──→ 参照の種類 ("call" 等)       │
└─────────────────────────────────────────┘
```

2. **具体例**: completeTask() → findById() の呼び出しを Reference で表現
   - SampleProject.java の110行目 `Task task = findById(id);` に対応
   - ref.scope().name() → "completeTask"
   - ref.ent().name() → "findById"
   - ref.file().name() → "SampleProject.java"
   - ref.line() → 110

3. **双方向アクセスの図解**:
```
順方向: completeTask.refs("call", "method", true)
  completeTask ──call──→ findById    ← この Reference が返る

逆方向: findById.refs("callby", "method", true)
  completeTask ──callby──→ findById  ← この Reference が返る（視点が逆）
```

4. **対称性の説明**: call/callby は同じ関係を異なる視点から見たもの
   - 表で整理: call = 順方向（呼ぶ側から見る）、callby = 逆方向（呼ばれる側から見る）

5. **refs() の第3引数 unique の説明**: true で重複除外（同じ関数を複数回呼んでいても1つだけ返る）

**Step 2: コミット**

```bash
git add docs/understand-java-api/function-call-mapping.md
git commit -m "docs: add direct call mapping section with diagrams"
```

---

### Task 3: セクション3「呼び出し元関数名を取得する」を執筆

**Files:**
- Modify: `docs/understand-java-api/function-call-mapping.md`

**Step 1: セクション3を追記**

ユーザーの核心的な問い「呼び出し元関数名も取得できるのか？」に答えるセクション。

以下の内容を含める:

1. **問いの定義**: 「findById() を呼んでいる関数は何か？」

2. **思考プロセスを段階的に図解**:
```
Step 1: findById の Entity を取得
  db.ents("method") → findById Entity

Step 2: callby で逆方向参照を取得
  findById.refs("callby", "method", true) → Reference[]

Step 3: 各 Reference の scope() で呼び出し元を取得
  ref.scope() → completeTask Entity

Step 4: 呼び出し元の名前を取得
  ref.scope().name() → "completeTask"
```

3. **コード例**（03-code-exploration.md のスタイルに準拠）:
```java
// findById を呼び出している関数を一覧表示
Entity[] methods = db.ents("method");
for (Entity method : methods) {
    if (method.name().equals("findById")) {
        Reference[] callers = method.refs("callby", "method", true);
        System.out.println("=== " + method.longname() + " の呼び出し元 ===");
        for (Reference ref : callers) {
            System.out.printf("  ← %s (ファイル: %s, 行: %d)%n",
                ref.scope().longname(),
                ref.file().name(),
                ref.line());
        }
    }
}
```

4. **出力例**:
```
=== sample.TaskManager.findById の呼び出し元 ===
  ← sample.TaskManager.completeTask (ファイル: SampleProject.java, 行: 110)
```

5. **name() vs longname() の使い分け**:
   - name() → "completeTask"（短い、表示用）
   - longname() → "sample.TaskManager.completeTask"（完全修飾、一意性が必要な場合）

6. **所属クラスの取得**: scope() で得た Entity から definein でクラスを逆引き
```java
Entity caller = ref.scope();
Reference[] classRefs = caller.refs("definein", "class", true);
if (classRefs.length > 0) {
    String className = classRefs[0].ent().name(); // → "TaskManager"
}
```

**Step 2: コミット**

```bash
git add docs/understand-java-api/function-call-mapping.md
git commit -m "docs: add caller function name retrieval section"
```

---

### Task 4: セクション4「呼び出しチェーンの探索」を執筆

**Files:**
- Modify: `docs/understand-java-api/function-call-mapping.md`

**Step 1: セクション4を追記**

以下の内容を含める:

1. **チェーンの概念図**（SampleProject.java の実際の呼び出し関係）:
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

2. **「1段ずつたどる」考え方の解説**:
   - 起点: main() の Entity
   - 1段目: main().refs("call", "method", true) → [addTask, completeTask, printAll, countCompleted]
   - 2段目: completeTask().refs("call", "method", true) → [findById, complete]
   - 3段目: findById().refs("call", "method", true) → [getId]
   - これを自動化するのが再帰探索

3. **再帰探索のコード例**:
```java
/**
 * 指定メソッドから始まる呼び出しチェーンを表示する。
 * @param method 起点メソッドの Entity
 * @param depth 現在の深さ（インデント用）
 * @param visited 訪問済みセット（サイクル防止）
 */
private static void printCallChain(Entity method, int depth, Set<String> visited) {
    String indent = "  ".repeat(depth);
    System.out.println(indent + method.longname());

    // サイクル防止: uniquename で訪問済みチェック
    if (visited.contains(method.uniquename())) {
        System.out.println(indent + "  (循環参照 - 省略)");
        return;
    }
    visited.add(method.uniquename());

    Reference[] callRefs = method.refs("call", "method", true);
    for (Reference ref : callRefs) {
        printCallChain(ref.ent(), depth + 1, visited);
    }
}
```

4. **サイクル防止の重要性の説明**:
   - 相互再帰（A→B→A）が存在しうる
   - uniquename() を使う理由: name() だとオーバーロードで衝突する可能性
   - Set<String> で管理

5. **深さ制限の考え方**: 大規模コードベースでは maxDepth パラメータを追加する手法を簡潔に紹介

**Step 2: コミット**

```bash
git add docs/understand-java-api/function-call-mapping.md
git commit -m "docs: add call chain traversal section"
```

---

### Task 5: セクション5「影響分析：callby の再帰探索」を執筆

**Files:**
- Modify: `docs/understand-java-api/function-call-mapping.md`

**Step 1: セクション5を追記**

以下の内容を含める:

1. **問いの定義**: 「findById() を変更したら、どの関数に影響があるか？」

2. **影響伝搬の図解**:
```
影響の方向（callby を逆にたどる）:

findById ←── completeTask ←── main

「findById を変更すると completeTask に影響し、
 completeTask の動作変更は main にも波及する可能性がある」
```

3. **セクション4との対比**:
   - セクション4: refs("call") を再帰 → 「この関数から何が呼ばれるか」（下流）
   - セクション5: refs("callby") を再帰 → 「この関数は何に影響するか」（上流）
   - 同じ再帰パターンで方向だけが異なる

4. **コード例**（セクション4の printCallChain を callby 版に変えたもの）:
```java
/**
 * 指定メソッドの影響範囲を表示する。
 * @param method 起点メソッドの Entity
 * @param depth 現在の深さ（インデント用）
 * @param visited 訪問済みセット（サイクル防止）
 */
private static void printImpactScope(Entity method, int depth, Set<String> visited) {
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

5. **出力例**:
```
sample.TaskManager.findById
  sample.TaskManager.completeTask
    sample.SampleProject.main
```

6. **実務での活用ポイント**（簡潔に）:
   - リファクタリング前の影響調査
   - テスト範囲の特定

**Step 2: コミット**

```bash
git add docs/understand-java-api/function-call-mapping.md
git commit -m "docs: add impact analysis section with callby traversal"
```

---

### Task 6: セクション6「まとめ」を執筆し最終確認

**Files:**
- Modify: `docs/understand-java-api/function-call-mapping.md`

**Step 1: セクション6を追記**

以下の内容を含める:

1. **パターン早見表**:

| やりたいこと | API の使い方 | kindstring |
|---|---|---|
| この関数が呼ぶ関数一覧 | `method.refs("call", "method", true)` | call |
| この関数を呼ぶ関数一覧 | `method.refs("callby", "method", true)` | callby |
| 呼び出し元の関数名 | `ref.scope().name()` | — |
| 呼び出し先の関数名 | `ref.ent().name()` | — |
| 呼び出し元の所属クラス | `ref.scope().refs("definein", "class", true)` | definein |
| 呼び出しチェーン（下流） | `refs("call")` を再帰的にたどる | call |
| 影響分析（上流） | `refs("callby")` を再帰的にたどる | callby |

2. **関連ドキュメントへのリンク**:
   - 02-core-concepts.md — Entity, Reference の基本概念
   - 03-code-exploration.md — ユースケース3「メソッド呼び出し関係」
   - 04-dependency-analysis.md — CSV 出力による呼び出し関係の一括エクスポート
   - 05-api-reference.md — Entity.refs(), Reference.scope() 等の API 詳細

**Step 2: ドキュメント全体の整合性を確認**

確認項目:
- 全セクションで SampleProject.java の同じ呼び出し関係を使っているか
- 技術用語の表記が既存ドキュメントと統一されているか
- コード例が API 準拠のスニペットになっているか（擬似コードではない）
- 見出しレベルが一貫しているか

**Step 3: コミット**

```bash
git add docs/understand-java-api/function-call-mapping.md
git commit -m "docs: add summary and complete function call mapping guide"
```
