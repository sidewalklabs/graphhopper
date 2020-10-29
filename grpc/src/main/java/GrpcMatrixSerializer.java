import com.graphhopper.routing.*;

import java.util.*;
import java.util.stream.Collectors;

import router.RouterOuterClass.*;

public class GrpcMatrixSerializer {

    public static MatrixRouteReply serialize(GHMRequest request, GHMResponse response) {
        if (request.getFailFast() && response.hasInvalidPoints()) {
            MatrixErrors matrixErrors = new MatrixErrors();
            matrixErrors.addInvalidFromPoints(response.getInvalidFromPoints());
            matrixErrors.addInvalidToPoints(response.getInvalidToPoints());
            throw new MatrixCalculationException(matrixErrors);
        } else {
            int from_len = request.getFromPoints().size();
            int to_len = request.getToPoints().size();
            List<List<Long>> timeList = new ArrayList(from_len);
            List<Long> timeRow;
            List<List<Long>> distanceList = new ArrayList(from_len);
            List<Long> distanceRow;
            Iterator<MatrixElement> iter = response.getMatrixElementIterator();
            MatrixErrors matrixErrors = new MatrixErrors();
            StringBuilder debugBuilder = new StringBuilder();
            debugBuilder.append(response.getDebugInfo());

            for(int fromIndex = 0; fromIndex < from_len; ++fromIndex) {
                timeRow = new ArrayList(to_len);
                timeList.add(timeRow);
                distanceRow = new ArrayList(to_len);
                distanceList.add(distanceRow);

                for(int toIndex = 0; toIndex < to_len; ++toIndex) {
                    if (!iter.hasNext()) {
                        throw new IllegalStateException("Internal error, matrix dimensions should be " + from_len + "x" + to_len + ", but failed to retrieve element (" + fromIndex + ", " + toIndex + ")");
                    }

                    MatrixElement element = iter.next();
                    if (!element.isConnected()) {
                        matrixErrors.addDisconnectedPair(element.getFromIndex(), element.getToIndex());
                    }

                    if (request.getFailFast() && matrixErrors.hasDisconnectedPairs()) {
                        throw new MatrixCalculationException(matrixErrors);
                    }

                    long time = element.getTime();
                    timeRow.add(time == 9223372036854775807L ? null : Math.round((double)time / 1000.0D));

                    double distance = element.getDistance();
                    distanceRow.add(distance == 1.7976931348623157E308D ? null : Math.round(distance));

                    debugBuilder.append(element.getDebugInfo());
                }
            }

            List<MatrixRow> timeRows = timeList.stream()
                    .map(row -> MatrixRow.newBuilder().addAllValues(row).build()).collect(Collectors.toList());
            List<MatrixRow> distanceRows = distanceList.stream()
                    .map(row -> MatrixRow.newBuilder().addAllValues(row).build()).collect(Collectors.toList());

            return MatrixRouteReply.newBuilder().addAllTimes(timeRows).addAllDistances(distanceRows).build();
        }
    }
}
