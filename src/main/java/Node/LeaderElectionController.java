package Node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.concurrent.locks.ReadWriteLock;

@Controller
@Slf4j
public class LeaderElectionController {
    private final LeaderElectionService leaderElectionService;
    private final SimpMessagingTemplate template;
    private final ThisNodeInfo thisNodeInfo;
    private final LeaderElectionService.Vote vote;
    private final ReadWriteLock sendingInitialLeaderElectionMessage;
    private final NodeMessageRoundSynchronizer<LeaderElectionMessage> leaderElectionRoundSynchronizer;

    private final Runnable leaderElectionWork;

    @Autowired
    public LeaderElectionController(
            LeaderElectionService leaderElectionService,
            SimpMessagingTemplate template,
            @Qualifier("Node/NodeConfigurator/thisNodeInfo")
            ThisNodeInfo thisNodeInfo,
            @Qualifier("Node/LeaderElectionConfig/sendingInitialLeaderElectionMessage")
            ReadWriteLock sendingInitialLeaderElectionMessage,
            @Qualifier("Node/LeaderElectionConfig/leaderElectionRoundSynchronizer")
            NodeMessageRoundSynchronizer<LeaderElectionMessage> leaderElectionRoundSynchronizer,
            ){
        this.leaderElectionService = leaderElectionService;
        this.template = template;
        this.thisNodeInfo = thisNodeInfo;
        this.vote = leaderElectionService.getVote();
        this.sendingInitialLeaderElectionMessage = sendingInitialLeaderElectionMessage;
        this.leaderElectionRoundSynchronizer = leaderElectionRoundSynchronizer;

        leaderElectionWork = () -> {
            leaderElectionService.processNeighborlyAdvice(leaderElectionRoundSynchronizer.getMessagesThisRound());
        };
    }

    @MessageMapping("/leaderElection")
    public void leaderElection(LeaderElectionMessage message) {
        if(leaderElectionService.hasLeader()) {
            if (log.isDebugEnabled()) {
                log.debug("<---ignoring leader election message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received leader election message {}", message);
            }
            sendingInitialLeaderElectionMessage.readLock().lock();
            try {
                synchronized (this) {
                    leaderElectionRoundSynchronizer.enqueueAndRunIfReady(
                            message,
                            leaderElectionWork
                    );
                }
            } finally {
                sendingInitialLeaderElectionMessage.readLock().unlock();
            }
        }
    }

    @MessageMapping("/leaderAnnounce")
    public void leaderAnnounce(LeaderAnnounceMessage message) {
        if(log.isDebugEnabled()) {
            log.debug("<---received leader announce message {}", message);
        }
        leaderElectionService.leaderAnnounce(message.getLeaderUid(), message.getDistance());
    }

        }
    }

    public void sendLeaderElection() throws MessagingException {
        LeaderElectionMessage message = new LeaderElectionMessage(
                thisNodeInfo.getUid(),
                leaderElectionRoundSynchronizer.getRoundNumber(),
                vote.getMaxUidSeen(),
                vote.getMaxDistanceSeen()
                );
        if(log.isDebugEnabled()){
            log.debug("--->sending leader election message: {}", message);
        }
        template.convertAndSend("/topic/leaderElection", message);
        log.trace("leader election message sent");
    }

    public void announceLeader(int leaderUid) throws MessagingException {
        LeaderAnnounceMessage message = new LeaderAnnounceMessage(
                thisNodeInfo.getUid(),
                leaderUid,
                leaderElectionService.getDistanceToNeighborFromRoot()
        );
        if(log.isDebugEnabled()){
            log.debug("--->sending leader announce message: {}", message);
        }
        template.convertAndSend("/topic/leaderAnnounce", message);
        log.trace("done sending the leader announce message");
    }

    public void sendLeaderElection() throws MessagingException {
        LeaderElectionMessage message = new LeaderElectionMessage(
                thisNodeInfo.getUid(),
                roundNumber,
                vote.getMaxUidSeen(),
                vote.getMaxDistanceSeen()
                );
        if(log.isDebugEnabled()){
            log.debug("--->sending leader election message: {}", message);
        }
        template.convertAndSend("/topic/leaderElection", message);
        log.trace("leader election message sent");
    }
}