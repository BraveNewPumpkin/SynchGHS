package Node;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NodeIncrementableRoundSynchronizer<T extends RoundSynchronizable> extends NodeMessageRoundSynchronizer<T> {
    private final List<Integer> roundProgress;

    public NodeIncrementableRoundSynchronizer(int roundSize) {
        super(roundSize);
        System.out.println("SRS Round Size: "+ roundSize);
        roundProgress = new ArrayList<>();
    }

    @Override
    public void enqueueMessage(T message) {
        int messageRoundNumber = message.getRoundNumber();
        incrementProgressForRound(messageRoundNumber);

        super.enqueueMessage(message);
    }

    public void incrementProgressForRound(int roundNumber) {
        if(roundProgress.size() <= roundNumber) {
            for(int i = roundProgress.size(); i <= roundNumber; i++) {
                roundProgress.add(0);
            }
        }
        int selectedRoundProgress = roundProgress.get(roundNumber);
        selectedRoundProgress++;
        System.out.println("inside incrementProgressForRound Roundnumber: "+ roundNumber + "selectedRoundProgress: " +selectedRoundProgress);
        roundProgress.set(roundNumber, selectedRoundProgress);
        ensureQueueForRoundIsInitialized(roundNumber);
    }

    public int getProgressForRound(int roundNumber) {
        return roundProgress.get(roundNumber);
    }

    public synchronized void incrementProgressAndRunIfReady(int roundNumber, Runnable work) {
        incrementProgressForRound(roundNumber);
        //only try to run if the round we're progressing is current round
        if(log.isTraceEnabled()) {
            log.trace("we are in round: {} message is in round: {}", getRoundNumber(), roundNumber);
        }
        if(getRoundNumber() == roundNumber) {
            runIfReady(work);
        }
    }

    @Override
    public void runIfReady(Runnable work) {
        int progressSoFarThisRound = roundProgress.get(getRoundNumber());
        System.out.println("inside SRS run if ready, progressSoFarThisRound: "+ progressSoFarThisRound);
        if (progressSoFarThisRound == getRoundSize()) {
            work.run();
        }
    }

    @Override
    public void reset() {
        super.reset();
        roundProgress.clear();
    }
}
