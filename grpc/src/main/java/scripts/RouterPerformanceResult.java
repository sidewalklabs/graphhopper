package scripts;

import java.util.List;

public class RouterPerformanceResult {
    public String from;
    public String to;
    public String departureTime;
    public boolean usePareto;
    public double duration;
    public List<Integer> numTransfers;
    public boolean error;

    public RouterPerformanceResult(String from, String to, String departureTime, boolean usePareto,
                                   double duration, List<Integer> numTransfers, boolean error) {
        this.from = from;
        this.to = to;
        this.departureTime = departureTime;
        this.usePareto = usePareto;
        this.duration = duration;
        this.numTransfers = numTransfers;
        this.error = error;
    }
}
