import com.scitools.understand.*;

/**
 * Understand Java API の基本的な使い方を示すサンプル。
 *
 * 使い方:
 *   java -cp "Understand.jar;." BasicUsage <UDBファイルパス>
 */
public class BasicUsage {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("使い方: java BasicUsage <UDBファイルパス>");
            System.exit(1);
        }

        String dbPath = args[0];
        Database db = null;

        try {
            // データベースを開く
            db = Understand.open(dbPath);
            System.out.println("データベースを開きました: " + db.name());

            // 対応言語を表示
            String[] languages = db.language();
            System.out.println("言語: " + String.join(", ", languages));

            // 全エンティティを取得して表示
            Entity[] allEntities = db.ents(null);
            System.out.println("エンティティ総数: " + allEntities.length);

            // クラスエンティティのみ取得
            Entity[] classes = db.ents("class");
            System.out.println("\n--- クラス一覧 ---");
            for (Entity cls : classes) {
                System.out.printf("  %s (種別: %s)%n", cls.longname(), cls.kind().name());
            }

            // メソッドエンティティのみ取得
            Entity[] methods = db.ents("method");
            System.out.println("\n--- メソッド一覧 ---");
            for (Entity method : methods) {
                System.out.printf("  %s (種別: %s)%n", method.longname(), method.kind().name());
            }

        } catch (UnderstandException e) {
            System.err.println("エラー: " + e.getMessage());
            System.exit(1);
        } finally {
            // 必ずデータベースを閉じる
            if (db != null) {
                db.close();
                System.out.println("\nデータベースを閉じました。");
            }
        }
    }
}
