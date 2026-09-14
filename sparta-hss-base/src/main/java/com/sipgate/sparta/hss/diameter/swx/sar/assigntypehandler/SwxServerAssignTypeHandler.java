package com.sipgate.sparta.hss.diameter.swx.sar.assigntypehandler;

import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.swx.messages.ServerAssignmentRequest;

/// Strategy interface for SWx Server-Assignment-Request handlers, mirroring the Cx
/// {@code ServerAssignTypeHandler} shape but without the Cx Public-Identity parameter: the SWx SAR
/// carries the AAA-Server identity in Origin-Host and the IMSI as the User-Name prefix, so a
/// handler only needs the request itself.
public interface SwxServerAssignTypeHandler {

    ServerAssignmentAnswer.Out handle(ServerAssignmentRequest.In request);

    /// Stamps User-Name onto the answer when the request carries one (TS 29.273: every SAA echoes
    /// the User-Name). Auth-Session-State and Vendor-Specific-Application-Id are already stamped by
    /// {@code DiameterMessageFactory.createAnswer} (lib 0.1.15).
    static ServerAssignmentAnswer.Out stampUserName(
        final ServerAssignmentRequest.In request, final ServerAssignmentAnswer.Out answer)
    {
        if (request.getUserName() != null) {
            answer.setUserName(request.getUserName());
        }
        return answer;
    }
}
