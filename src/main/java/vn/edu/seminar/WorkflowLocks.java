package vn.edu.seminar;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
/** Serializes retries for one submission across sessions in this single-instance demo. */
@Component
public class WorkflowLocks {
    private final Object[] stripes = new Object[128];
    public WorkflowLocks() { for (int i = 0; i < stripes.length; i++) stripes[i] = new Object(); }
    public <T> T run(String id, Supplier<T> action) {
        synchronized (stripes[Math.floorMod(id.hashCode(), stripes.length)]) { return action.get(); }
    }
}
