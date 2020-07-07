package de.tudarmstadt.informatik.hostage.protocol.mqttUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import de.tudarmstadt.informatik.hostage.Hostage;
import de.tudarmstadt.informatik.hostage.HostageApplication;
import de.tudarmstadt.informatik.hostage.commons.HelperUtils;
import de.tudarmstadt.informatik.hostage.commons.SubnetUtils;
import de.tudarmstadt.informatik.hostage.logging.AttackRecord;
import de.tudarmstadt.informatik.hostage.logging.DaoSession;
import de.tudarmstadt.informatik.hostage.logging.MessageRecord;
import de.tudarmstadt.informatik.hostage.logging.SyncDevice;
import de.tudarmstadt.informatik.hostage.persistence.DAO.DAOHelper;
import de.tudarmstadt.informatik.hostage.protocol.MQTT;
import de.tudarmstadt.informatik.hostage.protocol.Protocol;
import io.moquette.broker.ClientDescriptor;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.InterceptAcknowledgedMessage;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptConnectionLostMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptUnsubscribeMessage;

public class MQTTHandler {
    private static ArrayList<InterceptPublishMessage> publishMessages = new ArrayList<>();
    private static   ArrayList<InterceptPublishMessage> currentPublishMessages = new ArrayList<>();

    private static ArrayList<InterceptConnectMessage> interceptConnectMessages = new ArrayList<>();
    private static   ArrayList<InterceptConnectMessage> currentConnectedMessages = new ArrayList<>();

    private static ArrayList<InterceptDisconnectMessage> interceptDisconnectMessages = new ArrayList<>();
    private static ArrayList<InterceptConnectionLostMessage> interceptConnectionLostMessages = new ArrayList<>();

    private static ArrayList<InterceptSubscribeMessage> interceptSubscribeMessages = new ArrayList<>();
    private static   ArrayList<InterceptSubscribeMessage> currentSubscribeMessages = new ArrayList<>();

    private static ArrayList<InterceptUnsubscribeMessage> interceptUnsubscribeMessages = new ArrayList<>();
    private static ArrayList<InterceptAcknowledgedMessage> interceptAcknowledgedMessages = new ArrayList<>();

    private final static int brokerPort = 1883;

    /**
     * Intercepts all the captured packets from the broker and adds the to the appropriate list.
     * @return
     */

    public InterceptHandler getHandler(){
        Class<?>[] ALL_MESSAGE_TYPES = {InterceptConnectMessage.class, InterceptDisconnectMessage.class,
                InterceptConnectionLostMessage.class, InterceptPublishMessage.class, InterceptSubscribeMessage.class,
                InterceptUnsubscribeMessage.class, InterceptAcknowledgedMessage.class};

        InterceptHandler handler = new InterceptHandler() {
            @Override
            public String getID() {
                return null;
            }

            @Override
            public Class<?>[] getInterceptedMessageTypes() {
                return ALL_MESSAGE_TYPES;
            }

            @Override
            public void onConnect(InterceptConnectMessage interceptConnectMessage) {
                interceptConnectMessages.add(interceptConnectMessage);
                currentConnectedMessages.add(interceptConnectMessage);

            }

            @Override
            public void onDisconnect(InterceptDisconnectMessage interceptDisconnectMessage) {
                interceptDisconnectMessages.add(interceptDisconnectMessage);

            }

            @Override
            public void onConnectionLost(InterceptConnectionLostMessage interceptConnectionLostMessage) {
                interceptConnectionLostMessages.add(interceptConnectionLostMessage);

            }

            @Override
            public void onPublish(InterceptPublishMessage interceptPublishMessage) {
                // publishMessages.add(interceptPublishMessage);
                currentPublishMessages.add(interceptPublishMessage);

            }

            @Override
            public void onSubscribe(InterceptSubscribeMessage interceptSubscribeMessage) {
                // interceptSubscribeMessages.add(interceptSubscribeMessage);
                currentSubscribeMessages.add(interceptSubscribeMessage);
            }

            @Override
            public void onUnsubscribe(InterceptUnsubscribeMessage interceptUnsubscribeMessage) {
                interceptUnsubscribeMessages.add(interceptUnsubscribeMessage);
            }

            @Override
            public void onMessageAcknowledged(InterceptAcknowledgedMessage interceptAcknowledgedMessage) {
                interceptAcknowledgedMessages.add(interceptAcknowledgedMessage);
            }
        };
        return handler;
    }

    public static ArrayList<InterceptPublishMessage> getPublishMessages() {
        return publishMessages;
    }

    public static ArrayList<InterceptConnectMessage> getInterceptConnectMessages() {
        return interceptConnectMessages;
    }

    public static ArrayList<InterceptDisconnectMessage> getInterceptDisconnectMessages() {
        return interceptDisconnectMessages;
    }

    public static ArrayList<InterceptConnectionLostMessage> getInterceptConnectionLostMessages() {
        return interceptConnectionLostMessages;
    }

    public static ArrayList<InterceptSubscribeMessage> getInterceptSubscribeMessages() {
        return interceptSubscribeMessages;
    }

    public static ArrayList<InterceptUnsubscribeMessage> getInterceptUnsubscribeMessages() {
        return interceptUnsubscribeMessages;
    }

    public static ArrayList<InterceptAcknowledgedMessage> getInterceptAcknowledgedMessages() {
        return interceptAcknowledgedMessages;
    }

    public static ArrayList<InterceptPublishMessage> getCurrentPublishMessages() {
        return currentPublishMessages;
    }

    public static ArrayList<InterceptConnectMessage> getCurrentConnectedMessages() {
        return currentConnectedMessages;
    }

    public static ArrayList<InterceptSubscribeMessage> getCurrentSubscribeMessages() {
        return currentSubscribeMessages;
    }

    /**
     * Checks if there is an ongoing attack, without confusing the SensorProfile client as an attacker.
     * @return
     */

    public static boolean isAnAttackOngoing(){
        return isAnAttackerConnected(getCurrentConnectedMessages());
    }

    /**
     * Checks if a topic is published from an Attacker and updates the record.
     */

    public static void isTopicPublished(){
        boolean isAnAttackerConnected = isAnAttackerConnected(currentPublishMessages);
        if(isAnAttackerConnected){
            if(!currentPublishMessages.isEmpty()) {
                DaoSession dbSession = HostageApplication.getInstances().getDaoSession();

                DAOHelper daoHelper = new DAOHelper(dbSession);
                MessageRecord record = daoHelper.getMessageRecordDAO().getLastedInsertedRecord();
                record.setPacket(getPublishedTopics());
                daoHelper.getMessageRecordDAO().updateRecord(record);

                currentPublishMessages.clear();
            }
        }

    }

    private static boolean isAnAttackerConnected(ArrayList<?> connectedMessages){
        Collection<ClientDescriptor> clients = MQTT.listConnectedClients();

        if(!clients.isEmpty() && !connectedMessages.isEmpty()) {
            for (ClientDescriptor item : clients) {
                if (item != null) {
                    if(item.getClientID().equals(SensorProfile.getClientID()) && clients.size()==1)
                        return false;
                }
            }
            return true;
        }
        return false;


    }

    /**
     * Gets the IP address of the current connected client
     * @return
     */
    private static String getIPCurrentClient() {
        Collection<ClientDescriptor> clients = MQTT.listConnectedClients();
        String  ipAddress = "localhost";
        if(!clients.isEmpty() && !getCurrentConnectedMessages().isEmpty()) {
            for (ClientDescriptor item : clients) {
                if(item!=null){
                    if (item.getClientID().equals(getCurrentConnectedMessages()
                            .get(currentConnectedMessages.size()-1).getClientID())) { //ArrayList preserves order of inserted items.
                        ipAddress = item.getAddress();
                    }
                }
            }
        }
        return ipAddress;
    }

    private static String getPublishedTopics(){
        String packet = "";

        if(!currentPublishMessages.isEmpty()){
            InterceptPublishMessage message = currentPublishMessages.get(0);
                if(message!=null) {
                    if (message.getClientID().equals(interceptConnectMessages.get(interceptConnectMessages.size()-1).getClientID())) {

                        packet+="TopicName: "+message.getTopicName()+" "+
                                "Message Clientid: "+message.getClientID()+
                                "/n";
                    }
                }

        }
        return packet;
    }

    /**
     * Gets the port number that the client client connected.
     * @return
     */

    private static int getPortCurrentClient() {
        Collection<ClientDescriptor> clients = MQTT.listConnectedClients();
        int  port = 0;
        if(!clients.isEmpty() && !getCurrentConnectedMessages().isEmpty()) {

            for (ClientDescriptor item : clients) {
                if (item.getClientID().equals(getCurrentConnectedMessages().get(currentConnectedMessages.size()-1).getClientID())) {
                    port = item.getPort();
                }
            }
        }
        return port;
    }

    /**
     * Removes the current client in order to monitor a new attack
     */

    public static void removeCurrentConnected(){
        if(!currentConnectedMessages.isEmpty())
            currentConnectedMessages.clear();
    }

    /**
     * Helper method for Handler, creates an attackRecord with the logs from the InterceptHandler.
     * @param attack_id
     * @param externalIP
     * @param protocol
     * @param subnetMask
     * @param BSSID
     * @param internalIPAddress
     * @return
     * @throws UnknownHostException
     */

    public static AttackRecord createAttackRecord(Long attack_id, String externalIP, Protocol protocol,int subnetMask,String BSSID,int internalIPAddress) throws UnknownHostException {
        AttackRecord record = new AttackRecord();
        String internalIp = HelperUtils.intToStringIp(internalIPAddress);
        String remoteIp = getIPCurrentClient();

        record.setAttack_id(attack_id);
        record.setSync_id(attack_id);
        if(SyncDevice.currentDevice()!=null)
            record.setDevice(Objects.requireNonNull(SyncDevice.currentDevice()).getDeviceID());
        else
            record.setDevice(UUID.randomUUID().toString());
        record.setProtocol("MQTT");
        record.setExternalIP(externalIP);
        record.setLocalIP(internalIp);
        record.setLocalPort(brokerPort);
        int remoteIPAddress = HelperUtils.getInetAddress(InetAddress.getByName(remoteIp));
        record.setWasInternalAttack(checkIfIsInternalAttack(remoteIPAddress,internalIp));
        record.setRemoteIP(remoteIp);
        record.setRemotePort(getPortCurrentClient());
        record.setBssid(BSSID);

        removeCurrentConnected(); //removes the current client in order to be able to log the next attack
        return record;
    }

    private static boolean checkIfIsInternalAttack(int remoteIPAddress,String internalIPAddress){
        int prefix = Hostage.prefix;
        SubnetUtils utils = new SubnetUtils(internalIPAddress+"/"+prefix);
        String remoteIP = HelperUtils.intToStringIp(remoteIPAddress);

        return utils.getInfo().isInRange(remoteIP);
    }

    /**
     *Helper method for Handler, creates a messageRecord with the logs from the InterceptHandler.
     * @param type
     * @param attack_id
     * @return
     */

    public static MessageRecord createMessageRecord(MessageRecord.TYPE type, long attack_id) {
        MessageRecord record = new MessageRecord(true);
        record.setAttack_id(attack_id);
        record.setType(type);
        record.setTimestamp(System.currentTimeMillis());
        record.setPacket(getPublishedTopics());
        return record;
    }



}