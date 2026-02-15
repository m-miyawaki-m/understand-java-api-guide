package sample;

/**
 * Understand API の動作確認用サンプルプロジェクト。
 * このファイルを Understand で解析し、APIから情報を取得するデモに使用する。
 */

// --- インターフェース ---
interface Printable {
    String toDisplayString();
}

// --- 基底クラス ---
abstract class BaseItem implements Printable {
    private final int id;
    private final String name;

    public BaseItem(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toDisplayString() {
        return String.format("[%d] %s", id, name);
    }

    public abstract boolean isValid();
}

// --- 派生クラス: Task ---
class Task extends BaseItem {
    public enum Priority { LOW, MEDIUM, HIGH }

    private Priority priority;
    private boolean completed;

    public Task(int id, String name, Priority priority) {
        super(id, name);
        this.priority = priority;
        this.completed = false;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void complete() {
        this.completed = true;
    }

    @Override
    public boolean isValid() {
        return getName() != null && !getName().isEmpty();
    }

    @Override
    public String toDisplayString() {
        return super.toDisplayString() + " [" + priority + "]"
            + (completed ? " (完了)" : " (未完了)");
    }
}

// --- TaskManager: Task を管理するクラス ---
class TaskManager {
    private final java.util.List<Task> tasks = new java.util.ArrayList<>();

    public void addTask(Task task) {
        if (task.isValid()) {
            tasks.add(task);
        }
    }

    public Task findById(int id) {
        for (Task task : tasks) {
            if (task.getId() == id) {
                return task;
            }
        }
        return null;
    }

    public java.util.List<Task> getByPriority(Task.Priority priority) {
        java.util.List<Task> result = new java.util.ArrayList<>();
        for (Task task : tasks) {
            if (task.getPriority() == priority) {
                result.add(task);
            }
        }
        return result;
    }

    public void completeTask(int id) {
        Task task = findById(id);
        if (task != null) {
            task.complete();
        }
    }

    public void printAll() {
        for (Task task : tasks) {
            System.out.println(task.toDisplayString());
        }
    }

    public int countCompleted() {
        int count = 0;
        for (Task task : tasks) {
            if (task.isCompleted()) {
                count++;
            }
        }
        return count;
    }
}

// --- メインクラス ---
public class SampleProject {
    public static void main(String[] args) {
        TaskManager manager = new TaskManager();

        manager.addTask(new Task(1, "設計書作成", Task.Priority.HIGH));
        manager.addTask(new Task(2, "コードレビュー", Task.Priority.MEDIUM));
        manager.addTask(new Task(3, "テスト実行", Task.Priority.LOW));

        manager.completeTask(1);
        manager.printAll();

        System.out.println("完了数: " + manager.countCompleted());
    }
}
