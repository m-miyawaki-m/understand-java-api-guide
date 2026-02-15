# csv-all コマンド 詳細設計書

## 1. 背景と設計意図

### 1.1 なぜ変更するのか

旧 `csv` コマンドは `depends()` ベースの `依存元,依存先,参照数` フォーマットだった。
これは **モジュール間の結合度** を集約的に示すには適しているが、実際のユースケースでは以下の情報がより頻繁に必要とされる。

- 「このプロジェクトにどんなクラスがあるか」（クラス一覧）
- 「各クラスにどんなメソッドがあるか」（メソッド一覧）
- 「どのメソッドがどのメソッドを呼んでいるか」（呼び出し関係）

これらは Excel やスプレッドシートに取り込んで、フィルタ・ピボットテーブルで分析するユースケースが多い。

### 1.2 なぜ4ファイル分割か

単一CSVに全情報を詰め込むと、列数が異なるデータが混在し、Excel での扱いが困難になる。
正規化された4テーブル（クラス・メソッド・呼び出し・被呼び出し）に分割することで：

- 各CSVが独立してフィルタ・ソート可能
- `calls.csv` と `calledby.csv` は同じ関係の順方向・逆方向であり、用途に応じて選択可能
- RDB的に結合することで複合分析も可能（クラス名をキーに methods と calls を JOIN するなど）

### 1.3 なぜ `depends()` ではなく `refs()` を使うか

| API | 粒度 | 返すもの | 用途 |
|-----|------|---------|------|
| `depends()` / `dependsby()` | エンティティ単位 | `Map<Entity, Reference[]>` — 依存先エンティティと参照の集約 | モジュール間結合度の分析 |
| `refs(refkind, entkind, unique)` | 参照単位 | `Reference[]` — 個々の参照 | コード構造の詳細な走査 |

`depends()` はファイル間・クラス間の **集約的な依存関係** を返す。「クラス A がクラス B に 10 箇所で依存している」という情報は得られるが、**メソッドレベルの呼び出し関係** は得られない。

一方 `refs()` は参照種別（`define`, `call`, `callby`, `definein` 等）を指定して **個々の参照** を取得できる。これにより：

- `cls.refs("define", "method", true)` → クラスが定義しているメソッド一覧
- `method.refs("call", "method", true)` → メソッドが呼び出しているメソッド一覧
- `method.refs("callby", "method", true)` → メソッドを呼び出しているメソッド一覧

と、メソッドレベルの構造情報を正確に取得できる。

### 1.4 `call` と `callby` の対称性

Understand API の参照種別は **順方向と逆方向のペア** になっている。

```
call / callby の関係:

  method.refs("call", ...)   → このメソッドが呼んでいるメソッド（順方向）
  method.refs("callby", ...) → このメソッドを呼んでいるメソッド（逆方向）

  同じ参照関係を、どちら側から見るかの違い。
  A.refs("call") に B が含まれる ⟺ B.refs("callby") に A が含まれる
```

これに対応して `calls.csv`（順方向）と `calledby.csv`（逆方向）の2ファイルを出力する。
同じデータを別視点で提供しているため冗長に見えるが、ユースケースが異なる：

- **calls.csv**: 「あるメソッドが何を呼んでいるか」を起点に調べたいとき
- **calledby.csv**: 「あるメソッドがどこから呼ばれているか」を起点に調べたいとき

---

## 2. コマンド仕様

### 2.1 コマンドライン

```
java -cp "Understand.jar;." DependencyAnalyzer <UDBファイルパス> csv-all <出力ディレクトリ>
```

- 出力ディレクトリが存在しない場合は自動作成（`mkdirs()`）
- 出力ディレクトリが未指定の場合はエラーメッセージを出力して終了

### 2.2 出力ファイル

| ファイル名 | 内容 | 行数の目安 |
|-----------|------|-----------|
| `classes.csv` | クラス一覧 | クラス数 + 1（ヘッダ） |
| `methods.csv` | メソッド定義一覧 | 全メソッド数 + 1 |
| `calls.csv` | 関数呼び出し一覧 | 全呼び出し箇所数 + 1 |
| `calledby.csv` | 被呼び出し一覧 | 全被呼び出し箇所数 + 1 |

---

## 3. 各CSVの詳細設計

### 3.1 classes.csv（クラス一覧）

**目的**: プロジェクト内のクラス構成を一覧化する。

**ヘッダ**: `クラス名,種別,ファイル名,定義行`

| 列 | 取得元 | API |
|----|--------|-----|
| クラス名 | クラスの完全修飾名 | `cls.longname()` |
| 種別 | クラスの Kind 名（Java Class, Java Interface 等） | `cls.kind().name()` |
| ファイル名 | 定義されているファイル名 | `defRef.file().name()` |
| 定義行 | 定義の行番号 | `defRef.line()` |

**データ取得のロジック**:

```
1. db.ents("class ~unknown ~unresolved") でクラス一覧を取得
2. 各クラスに対して cls.refs("definein", null, true) で定義位置を取得
3. defRefs[0] からファイル名と行番号を取得
```

**`definein` を使う理由**:

クラスの定義位置を取得するには `definein` 参照を使う。これは「このエンティティがどこで定義されているか」を示す参照種別で、Reference の `file()` と `line()` から定義ファイルと行番号が得られる。

第2引数の entkindstring を `null` にしているのは、クラスの定義位置はファイルエンティティへの参照であり、"class" でフィルタすると取得できないため。

**SampleProject.java での想定出力例**:

```csv
クラス名,種別,ファイル名,定義行
sample.Printable,Java Interface,SampleProject.java,9
sample.BaseItem,Java Class Abstract,SampleProject.java,14
sample.Task,Java Class,SampleProject.java,40
sample.Task.Priority,Java Enum,SampleProject.java,41
sample.TaskManager,Java Class,SampleProject.java,81
sample.SampleProject,Java Class Public,SampleProject.java,134
```

### 3.2 methods.csv（メソッド定義一覧）

**目的**: 各クラスに属するメソッドの定義情報を一覧化する。

**ヘッダ**: `クラス名,メソッド名,戻り値型,ファイル名,定義行`

| 列 | 取得元 | API |
|----|--------|-----|
| クラス名 | メソッドが属するクラスの完全修飾名 | `cls.longname()` |
| メソッド名 | メソッドの短縮名 | `method.name()` |
| 戻り値型 | メソッドの戻り値の型 | `method.type()` |
| ファイル名 | 定義されているファイル名 | `ref.file().name()` |
| 定義行 | 定義の行番号 | `ref.line()` |

**データ取得のロジック**:

```
1. 各クラス cls に対して cls.refs("define", "method", true) を呼ぶ
2. 返された Reference 配列の各要素について:
   - ref.ent() → メソッドの Entity（name(), type() が取得可能）
   - ref.file().name() → 定義ファイル名
   - ref.line() → 定義行番号
```

**`define` 参照の意味**:

`cls.refs("define", "method", true)` は「このクラスが定義（define）しているメソッド（method）」を返す。
- 第1引数 `"define"`: 参照種別フィルタ。定義関係の参照のみを取得。
- 第2引数 `"method"`: エンティティ種別フィルタ。メソッドのみに絞り込む。
- 第3引数 `true`: unique フラグ。同一エンティティへの重複参照を除去。

ここでのポイントは、**起点がクラス** であること。クラスからたどることで「どのクラスに属するか」が自明になる。

### 3.3 calls.csv（関数呼び出し一覧）

**目的**: メソッドが呼び出している他のメソッドを一覧化する（順方向）。

**ヘッダ**: `呼び出し元クラス,呼び出し元メソッド,呼び出し先クラス,呼び出し先メソッド,ファイル名,呼び出し行`

| 列 | 取得元 | API |
|----|--------|-----|
| 呼び出し元クラス | 呼び出し元メソッドが属するクラス | `cls.longname()`（外側ループ） |
| 呼び出し元メソッド | 呼び出しを行っているメソッド | `method.name()` |
| 呼び出し先クラス | 呼び出し先メソッドが属するクラス | `getOwnerClassName(calledMethod)` |
| 呼び出し先メソッド | 呼び出されているメソッド | `calledMethod.name()` |
| ファイル名 | 呼び出しが発生しているファイル | `callRef.file().name()` |
| 呼び出し行 | 呼び出しの行番号 | `callRef.line()` |

**データ取得のロジック**:

```
1. 各クラス cls → cls.refs("define", "method", true) でメソッド一覧
2. 各メソッド method → method.refs("call", "method", true) で呼び出し先一覧
3. 各呼び出し先 calledMethod → getOwnerClassName(calledMethod) で所属クラスを逆引き
```

**3段階のネストの理由**:

```
クラス ──[define]──→ メソッド ──[call]──→ 呼び出し先メソッド
  │                    │                       │
  │                    │                       └─ 所属クラスを definein で逆引き
  │                    └─ 呼び出し元の情報
  └─ 呼び出し元クラスの情報
```

呼び出し元のクラスは外側ループの `cls` で自明だが、呼び出し先のクラスは `calledMethod` の Entity からは直接取得できない。そのため `getOwnerClassName()` ヘルパーで `definein` 逆引きを行う。

### 3.4 calledby.csv（被呼び出し一覧）

**目的**: メソッドを呼び出している他のメソッドを一覧化する（逆方向）。

**ヘッダ**: `対象クラス,対象メソッド,呼び出し元クラス,呼び出し元メソッド,ファイル名,呼び出し行`

| 列 | 取得元 | API |
|----|--------|-----|
| 対象クラス | 呼び出されているメソッドが属するクラス | `cls.longname()`（外側ループ） |
| 対象メソッド | 呼び出されているメソッド | `method.name()` |
| 呼び出し元クラス | 呼び出しを行っているメソッドが属するクラス | `getOwnerClassName(callerMethod)` |
| 呼び出し元メソッド | 呼び出しを行っているメソッド | `callerMethod.name()` |
| ファイル名 | 呼び出しが発生しているファイル | `callByRef.file().name()` |
| 呼び出し行 | 呼び出しの行番号 | `callByRef.line()` |

**データ取得のロジック**:

`calls.csv` と同じ構造だが、`method.refs("callby", "method", true)` で逆方向をたどる。

```
クラス ──[define]──→ メソッド ──[callby]──→ 呼び出し元メソッド
  │                    │                       │
  │                    │                       └─ 所属クラスを definein で逆引き
  │                    └─ 対象メソッドの情報
  └─ 対象クラスの情報
```

---

## 4. ヘルパーメソッドの設計

### 4.1 `getOwnerClassName(Entity method)` — 所属クラス逆引き

**必要性**: `calls.csv` / `calledby.csv` で、相手側メソッドの所属クラスを特定する必要がある。

**アプローチの選択肢と判断**:

| 方法 | メリット | デメリット |
|------|---------|-----------|
| (A) `method.refs("definein", "class", true)` | 正確。API が保証する定義関係をたどる | 呼び出しごとに追加の API 呼び出し |
| (B) `method.longname()` からクラス名を文字列パース | API 呼び出し不要で高速 | `longname()` のフォーマットに依存。ネストクラス等で不正確になりうる |
| (C) メソッド取得時にクラスとの対応を Map にキャッシュ | 高速。重複呼び出しを排除 | 実装が複雑化。サンプルコードとしての明瞭さが低下 |

**選択: (A)** — サンプルコードとしての明瞭さと正確さを優先。パフォーマンスはサンプルの規模では問題にならない。

**実装**:

```java
private static String getOwnerClassName(Entity method) {
    Reference[] defInRefs = method.refs("definein", "class", true);
    if (defInRefs.length > 0) {
        return defInRefs[0].ent().longname();
    }
    return "";
}
```

`definein` は `define` の逆方向。`method.refs("definein", "class", true)` は「このメソッドを定義している（definein）クラス（class）」を返す。

```
define / definein の関係:

  cls.refs("define", "method")     → クラスが定義しているメソッド
  method.refs("definein", "class") → メソッドを定義しているクラス（逆引き）
```

---

## 5. メソッド構成

### 5.1 全体構成図

```
main()
  └─ switch("csv-all")
       └─ exportAllCsv(db, outputDir)       ← オーケストレーター
            ├─ exportClassesCsv(classes, dir)
            ├─ exportMethodsCsv(classes, dir)
            ├─ exportCallsCsv(classes, dir)
            └─ exportCalledByCsv(classes, dir)
                 └─ getOwnerClassName(method)  ← calls/calledby で共用
```

### 5.2 設計判断: `Database` を渡すか `Entity[]` を渡すか

`exportAllCsv()` は `Database` を受け取り、そこで `db.ents("class ~unknown ~unresolved")` を1回だけ呼ぶ。
個別の export メソッドには結果の `Entity[] classes` を渡す。

**理由**:
- `db.ents()` の呼び出しは1回で十分（4メソッドで同じクラス一覧を使う）
- 個別メソッドが DB 全体を受け取ると、各メソッドの責務が曖昧になる
- `Entity[]` を渡すことで「このクラス一覧に対してCSVを出力する」という責務が明確になる

### 5.3 設計判断: ファイル出力に `File dir` を渡す

各 export メソッドは `File dir`（出力ディレクトリ）を受け取り、内部でファイル名を決定する。

**理由**:
- ファイル名（`classes.csv` 等）は各メソッドの内部仕様であり、呼び出し側が知る必要がない
- ディレクトリの存在確認・作成は `exportAllCsv()` の責務として集約

---

## 6. API 使用パターンまとめ

本実装で使用する Understand Java API のパターンを整理する。

### 6.1 エンティティ取得

```java
Entity[] classes = db.ents("class ~unknown ~unresolved");
```

- `~unknown ~unresolved` で、解析対象外のクラス（標準ライブラリ等）を除外
- ユースケース1・2 の `showFileDependencies()` / `showClassDependencies()` と同じパターン

### 6.2 参照種別と使い分け

| 呼び出しパターン | 参照種別 | 意味 | 使用箇所 |
|----------------|---------|------|---------|
| `cls.refs("definein", null, true)` | definein | クラスの定義位置（逆引き） | `exportClassesCsv()` |
| `cls.refs("define", "method", true)` | define | クラスが定義するメソッド | `exportMethodsCsv()`, `exportCallsCsv()`, `exportCalledByCsv()` |
| `method.refs("call", "method", true)` | call | メソッドが呼ぶメソッド | `exportCallsCsv()` |
| `method.refs("callby", "method", true)` | callby | メソッドを呼ぶメソッド | `exportCalledByCsv()` |
| `method.refs("definein", "class", true)` | definein | メソッドの所属クラス（逆引き） | `getOwnerClassName()` |

### 6.3 Reference オブジェクトから取得する情報

```java
Reference ref = ...;
ref.ent()          // 参照先の Entity
ref.file()         // 参照が発生しているファイルの Entity
ref.file().name()  // ファイル名（短縮名）
ref.line()         // 行番号
```

---

## 7. SampleProject.java に基づく想定出力

`SampleProject.java` を Understand で解析した場合の想定 CSV 出力。

### 7.1 classes.csv

```csv
クラス名,種別,ファイル名,定義行
sample.Printable,Java Interface,SampleProject.java,9
sample.BaseItem,Java Class Abstract,SampleProject.java,14
sample.Task,Java Class,SampleProject.java,40
sample.Task.Priority,Java Enum,SampleProject.java,41
sample.TaskManager,Java Class,SampleProject.java,81
sample.SampleProject,Java Class Public,SampleProject.java,134
```

### 7.2 methods.csv（一部抜粋）

```csv
クラス名,メソッド名,戻り値型,ファイル名,定義行
sample.BaseItem,BaseItem,void,SampleProject.java,18
sample.BaseItem,getId,int,SampleProject.java,23
sample.BaseItem,getName,String,SampleProject.java,27
sample.BaseItem,toDisplayString,String,SampleProject.java,32
sample.BaseItem,isValid,boolean,SampleProject.java,36
sample.Task,Task,void,SampleProject.java,46
sample.Task,getPriority,Priority,SampleProject.java,52
sample.Task,setPriority,void,SampleProject.java,56
sample.Task,isCompleted,boolean,SampleProject.java,60
sample.Task,complete,void,SampleProject.java,64
sample.Task,isValid,boolean,SampleProject.java,69
sample.Task,toDisplayString,String,SampleProject.java,74
sample.TaskManager,addTask,void,SampleProject.java,84
sample.TaskManager,findById,Task,SampleProject.java,90
sample.TaskManager,getByPriority,List,SampleProject.java,99
sample.TaskManager,completeTask,void,SampleProject.java,109
sample.TaskManager,printAll,void,SampleProject.java,116
sample.TaskManager,countCompleted,int,SampleProject.java,122
sample.SampleProject,main,void,SampleProject.java,135
```

### 7.3 calls.csv（一部抜粋）

```csv
呼び出し元クラス,呼び出し元メソッド,呼び出し先クラス,呼び出し先メソッド,ファイル名,呼び出し行
sample.Task,Task,sample.BaseItem,BaseItem,SampleProject.java,47
sample.Task,isValid,sample.BaseItem,getName,SampleProject.java,70
sample.Task,toDisplayString,sample.BaseItem,toDisplayString,SampleProject.java,75
sample.TaskManager,addTask,sample.Task,isValid,SampleProject.java,85
sample.TaskManager,findById,sample.Task,getId,SampleProject.java,92
sample.TaskManager,getByPriority,sample.Task,getPriority,SampleProject.java,102
sample.TaskManager,completeTask,sample.TaskManager,findById,SampleProject.java,110
sample.TaskManager,completeTask,sample.Task,complete,SampleProject.java,112
sample.TaskManager,printAll,sample.Task,toDisplayString,SampleProject.java,118
sample.TaskManager,countCompleted,sample.Task,isCompleted,SampleProject.java,125
sample.SampleProject,main,sample.TaskManager,addTask,SampleProject.java,138
sample.SampleProject,main,sample.TaskManager,completeTask,SampleProject.java,142
sample.SampleProject,main,sample.TaskManager,printAll,SampleProject.java,143
sample.SampleProject,main,sample.TaskManager,countCompleted,SampleProject.java,145
```

### 7.4 calledby.csv（一部抜粋）

```csv
対象クラス,対象メソッド,呼び出し元クラス,呼び出し元メソッド,ファイル名,呼び出し行
sample.BaseItem,BaseItem,sample.Task,Task,SampleProject.java,47
sample.BaseItem,getName,sample.Task,isValid,SampleProject.java,70
sample.BaseItem,toDisplayString,sample.Task,toDisplayString,SampleProject.java,75
sample.Task,isValid,sample.TaskManager,addTask,SampleProject.java,85
sample.Task,getId,sample.TaskManager,findById,SampleProject.java,92
sample.Task,getPriority,sample.TaskManager,getByPriority,SampleProject.java,102
sample.Task,complete,sample.TaskManager,completeTask,SampleProject.java,112
sample.Task,toDisplayString,sample.TaskManager,printAll,SampleProject.java,118
sample.Task,isCompleted,sample.TaskManager,countCompleted,SampleProject.java,125
sample.TaskManager,findById,sample.TaskManager,completeTask,SampleProject.java,110
sample.TaskManager,addTask,sample.SampleProject,main,SampleProject.java,138
sample.TaskManager,completeTask,sample.SampleProject,main,SampleProject.java,142
sample.TaskManager,printAll,sample.SampleProject,main,SampleProject.java,143
sample.TaskManager,countCompleted,sample.SampleProject,main,SampleProject.java,145
```

---

## 8. 変更ファイル一覧

| ファイル | 変更内容 |
|---------|---------|
| `docs/understand-java-api/samples/DependencyAnalyzer.java` | `csv` → `csv-all`、`exportDependenciesCsv()` 削除、6メソッド新規追加 |
| `docs/understand-java-api/04-dependency-analysis.md` | コマンド表更新、ユースケース3 全面差し替え、まとめ表更新 |

### 8.1 DependencyAnalyzer.java のメソッド構成（変更後）

| メソッド | 変更種別 | 説明 |
|---------|---------|------|
| `main()` | 変更 | `csv` case を `csv-all` case に変更 |
| `showFileDependencies()` | 変更なし | — |
| `showClassDependencies()` | 変更なし | — |
| `exportDependenciesCsv()` | **削除** | 旧 CSV 出力 |
| `exportAllCsv()` | **新規** | CSV 一括出力のオーケストレーター |
| `exportClassesCsv()` | **新規** | classes.csv 出力 |
| `exportMethodsCsv()` | **新規** | methods.csv 出力 |
| `exportCallsCsv()` | **新規** | calls.csv 出力 |
| `exportCalledByCsv()` | **新規** | calledby.csv 出力 |
| `getOwnerClassName()` | **新規** | メソッドの所属クラス逆引きヘルパー |

---

## 9. 制約と前提

- **外部ライブラリ不使用**: CSV 出力は `PrintWriter` + `FileWriter` のみ（CLAUDE.md の制約）
- **カンマ非エスケープ**: Java の識別子にはカンマが含まれないため、CSV エスケープは不要
- **文字エンコーディング**: `FileWriter` のデフォルト（システムエンコーディング）を使用
- **`~unknown ~unresolved` フィルタ**: 標準ライブラリ等の外部クラスを除外し、プロジェクト内のクラスのみを対象とする
- **コンパイル検証**: `Understand.jar` をクラスパスに指定した `javac` でコンパイルが通ることを確認済み（実行時の UDB ファイルは別途作成が必要）
