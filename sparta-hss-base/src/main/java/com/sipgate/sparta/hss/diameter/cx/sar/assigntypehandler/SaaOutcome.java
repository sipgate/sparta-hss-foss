package com.sipgate.sparta.hss.diameter.cx.sar.assigntypehandler;

import static com.sipgate.sparta.diameter._3gpp.common._3gppConstants.VENDOR_ID_3GPP;

import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentAnswer;
import com.sipgate.sparta.diameter._3gpp.cxdx.messages.ServerAssignmentRequest;
import com.sipgate.sparta.diameter.base.core.DiameterMessageFactory;
import com.sipgate.sparta.diameter.base.session.DiameterErrorAnswerException;

/**
 * Outcome of a Server-Assignment operation. Base Diameter result codes (RFC 6733) go into the
 * Result-Code AVP, 3GPP vendor-specific codes (TS 29.229, e.g. 2004/5011) must go into the
 * grouped Experimental-Result AVP instead. Error outcomes are raised as
 * {@link DiameterErrorAnswerException} so the answer is sent with the 'E' bit set (RFC 6733);
 * success outcomes (e.g. 2004) stay on a regular answer.
 */
public sealed interface SaaOutcome {

    ServerAssignmentAnswer.Out createAnswer(final ServerAssignmentRequest.In request) throws DiameterErrorAnswerException;

    record Success(long code) implements SaaOutcome {
        @Override
        public ServerAssignmentAnswer.Out createAnswer(final ServerAssignmentRequest.In request) {
            return DiameterMessageFactory.createAnswer(request, code);
        }
    }

    record ExperimentalSuccess(long code) implements SaaOutcome {
        @Override
        public ServerAssignmentAnswer.Out createAnswer(final ServerAssignmentRequest.In request) {
            return DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, code);
        }
    }

    record Error(long code) implements SaaOutcome {
        @Override
        public ServerAssignmentAnswer.Out createAnswer(final ServerAssignmentRequest.In request) throws DiameterErrorAnswerException {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createErrorAnswer(request, code));
        }
    }

    record ExperimentalError(long code) implements SaaOutcome {
        @Override
        public ServerAssignmentAnswer.Out createAnswer(final ServerAssignmentRequest.In request) throws DiameterErrorAnswerException {
            throw new DiameterErrorAnswerException(DiameterMessageFactory.createExperimentalResultAnswer(request, VENDOR_ID_3GPP, code));
        }
    }
}
