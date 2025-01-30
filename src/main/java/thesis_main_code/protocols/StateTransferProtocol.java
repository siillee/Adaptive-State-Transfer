package thesis_main_code.protocols;

import thesis_main_code.network.Message;

public interface StateTransferProtocol {

    void triggerStateTransfer(Message message, int currentEpoch);

    void processStateRequest(Message message);

    void processStateResponse(Message message);
}
