package com.klist.chatbot.search.fixture;

import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TouristSpotSearchQualityEvaluator {

    private TouristSpotSearchQualityEvaluator() {
    }

    public static Evaluation evaluate(
            List<SearchQualityCase> cases,
            TouristSpotSearchGateway searchGateway
    ) {
        List<CaseResult> results = cases.stream()
                .map(qualityCase -> evaluate(qualityCase, searchGateway.search(qualityCase.criteria())))
                .toList();
        return new Evaluation(
                metrics(results),
                groupedMetrics(results, result -> result.qualityCase().region()),
                groupedMetrics(results, result -> Integer.toString(result.qualityCase().contentTypeId())),
                results
        );
    }

    private static CaseResult evaluate(SearchQualityCase qualityCase, TouristSpotSearchResult result) {
        List<Long> rankedIds = result.evidence().stream()
                .map(evidence -> evidence.touristSpotId())
                .toList();
        int rank = rankedIds.indexOf(qualityCase.expectedTouristSpotId());
        return new CaseResult(qualityCase, rank < 0 ? null : rank + 1, rankedIds);
    }

    private static Map<String, Metrics> groupedMetrics(
            List<CaseResult> results,
            Function<CaseResult, String> classifier
    ) {
        return results.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> metrics(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private static Metrics metrics(List<CaseResult> results) {
        long top1 = results.stream().filter(result -> result.rank() != null && result.rank() == 1).count();
        long top3 = results.stream().filter(result -> result.rank() != null && result.rank() <= 3).count();
        return new Metrics(results.size(), top1, top3);
    }

    public record Evaluation(
            Metrics overall,
            Map<String, Metrics> byRegion,
            Map<String, Metrics> byContentType,
            List<CaseResult> cases
    ) {
    }

    public record Metrics(int total, long top1Hits, long top3Hits) {

        public double top1Rate() {
            return total == 0 ? 0 : (double) top1Hits / total;
        }

        public double top3Rate() {
            return total == 0 ? 0 : (double) top3Hits / total;
        }
    }

    public record CaseResult(
            SearchQualityCase qualityCase,
            Integer rank,
            List<Long> rankedTouristSpotIds
    ) {
    }
}
