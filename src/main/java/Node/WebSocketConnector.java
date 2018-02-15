package Node;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static Node.LambdaExceptionUtil.rethrowConsumer;

@Configuration
public class WebSocketConnector {

    @Autowired
    @Qualifier("Node/NodeConfigurator/thisNodeInfo")
    ThisNodeInfo thisNodeInfo;

    StompSessionHandler sessionHandler = new NodeStompSessionHandler();

    @Bean
    public List<StompSession> getConnections() throws InterruptedException, ExecutionException{
        ConcurrentLinkedQueue<StompSession> sessions = new ConcurrentLinkedQueue<>();
        //lambda to open connection and start sessions
        Consumer<NodeInfo> sessionBuildingLambda = rethrowConsumer(neighbor -> {
            WebSocketClient client = new StandardWebSocketClient();
            List<Transport> transports = new ArrayList<>(1);
            transports.add(new WebSocketTransport(client));
            SockJsClient sockJsClient = new SockJsClient(transports);
            WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());

            //TODO look up how to specify port
			String scheme = null;
			String user = String.valueOf(neighbor.getUid());
            String port = String.valueOf(neighbor.getPort());
            String path = null;
            Map<String, String> uriVars = new HashMap<>();
            uriVars.put("user", user);
            uriVars.put("port", port);
            StompSession session = stompClient.connect(neighbor.getHostName(), sessionHandler, uriVars).get();//, scheme, user, port, path);
            sessions.add(session);
        });
        //run the lambda in parallel for each neighboring node
        thisNodeInfo.getNeighbors().parallelStream().forEach(sessionBuildingLambda);

        return Collections.unmodifiableList(Arrays.asList(sessions.toArray(new StompSession[0])));
    }

    public void sendConnect() throws MessagingException {

        thisNodeInfo.getNeighbors().parallelStream().forEach(neighbor -> {
            ConnectMessage message = new ConnectMessage(thisNodeInfo.getUid(), neighbor.getUid());
            template.convertAndSend("/", message);
        });
    }
}