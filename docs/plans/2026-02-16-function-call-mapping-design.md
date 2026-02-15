# 設計書: Entity と Reference による関数呼び出しの紐づけ

## 概要

Entity と Reference を利用して関数の呼び出し関係を紐づける「考え方」を解説するドキュメントを作成する。
直接呼び出しの紐づけに加え、呼び出しチェーンの再帰的探索・影響分析パターンまでカバーする。

## 成果物

- `docs/understand-java-api/function-call-mapping.md` — 独立した日本語解説ドキュメント（番号体系の外）

## 対象読者

- Entity / Reference の基本を理解済みの開発者（02-core-concepts.md 読了レベル）
- 「関数の呼び出し元を取得したい」「呼び出しチェーンをたどりたい」というニーズを持つ人

## ドキュメント構成

### 1. はじめに
- 目的: Entity と Reference で関数呼び出し関係を紐づける「考え方」を理解する
- 前提知識: Entity, Reference, Kind, refs() の基本（02/03 へのリンク）
- 読了後にできること: 呼び出し元/先の取得、呼び出しチェーン探索、影響分析

### 2. 基本の考え方：直接呼び出しの紐づけ

Reference オブジェクトの構造を「誰が」「誰を」「どこで」の3要素で図解:

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

SampleProject.java の具体例（completeTask → findById）で各メソッドの返却値を示す。

双方向アクセスの解説:
- `method.refs("call", "method", true)` — この関数が呼ぶ関数を取得（順方向）
- `method.refs("callby", "method", true)` — この関数を呼ぶ関数を取得（逆方向）

call/callby の対称性: A.refs("call") に B が含まれる ⟺ B.refs("callby") に A が含まれる

### 3. 呼び出し元関数名を取得する

ユーザーの核心的な問いに答えるセクション。

思考プロセス:
1. 対象関数の Entity を取得（例: findById）
2. `refs("callby", "method", true)` で逆方向参照を取得
3. 各 Reference の `scope()` で呼び出し元 Entity を得る
4. `scope().name()` / `scope().longname()` で関数名を取得

コード例で name() と longname() の使い分けも説明:
- `name()` → "completeTask"（関数名のみ）
- `longname()` → "sample.TaskManager.completeTask"（完全修飾名）

所属クラスの取得: `scope().refs("definein", "class", true)` パターンも紹介。

### 4. 呼び出しチェーンの探索

再帰的に refs("call") をたどることで A→B→C を追跡する考え方。

具体例:
```
main() ─call─→ completeTask() ─call─→ findById() ─call─→ getId()
```

探索アルゴリズムの考え方:
- 起点 Entity から refs("call") で1段目を取得
- 各結果に対して再帰的に refs("call") を呼ぶ
- 訪問済みセット（Set<String>）でサイクル防止（uniquename() を使用）
- 深さ制限パラメータでの探索範囲制御

擬似コード（Java風）で再帰関数の構造を示す。

### 5. 影響分析：callby の再帰探索

「この関数を変更したら、どの関数に影響するか？」を callby で逆方向にたどるパターン。

具体例:
```
findById ←callby─ completeTask ←callby─ main
```

考え方はセクション4の逆方向版:
- refs("callby") を再帰的にたどる
- 同じくサイクル防止が必要
- 結果は「影響を受ける関数の一覧」

### 6. まとめ

パターン早見表:

| やりたいこと | 使う API | kindstring |
|---|---|---|
| この関数が呼ぶ関数一覧 | refs("call", "method", true) | call |
| この関数を呼ぶ関数一覧 | refs("callby", "method", true) | callby |
| 呼び出し元の関数名 | ref.scope().name() | — |
| 呼び出し先の関数名 | ref.ent().name() | — |
| 呼び出しチェーン | refs("call") を再帰 | call |
| 影響分析 | refs("callby") を再帰 | callby |

既存ドキュメントへのリンク（02, 03, 04, 05）。

## スタイルガイド

- 既存ドキュメントの表記ルールに準拠（CLAUDE.md 参照）
- 図解を多用（テキストベースの図）
- SampleProject.java の具体例を一貫して使用
- コード例は Java（擬似コードではなく実行可能なスニペット相当）
- 技術用語: Entity, Reference, Kind 等は英語で統一（初出時は日本語併記）

## 既存ドキュメントとの関係

- 02-core-concepts.md の「参照の方向」を前提知識としてリンク
- 03-code-exploration.md の「ユースケース3: メソッド呼び出し関係」を発展させた内容
- 04-dependency-analysis.md の CSV 出力とは別の切り口（考え方 vs 実装）
- 番号体系（01〜05）の外に配置し、トピック特化ドキュメントとして位置づける
