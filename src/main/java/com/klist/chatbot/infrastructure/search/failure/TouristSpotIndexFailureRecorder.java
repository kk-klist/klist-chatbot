package com.klist.chatbot.infrastructure.search.failure;

public interface TouristSpotIndexFailureRecorder {

    TouristSpotIndexFailureRecorder NO_OP = new TouristSpotIndexFailureRecorder() {
        @Override
        public void record(
                Long touristSpotId,
                TouristSpotIndexFailureOperation operation,
                String targetIndex,
                RuntimeException exception
        ) {
        }

        @Override
        public void resolve(
                Long touristSpotId,
                TouristSpotIndexFailureOperation operation,
                String targetIndex
        ) {
        }
    };

    void record(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex,
            RuntimeException exception
    );

    void resolve(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex
    );
}
