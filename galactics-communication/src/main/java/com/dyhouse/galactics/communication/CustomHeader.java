package com.dyhouse.galactics.communication;

import com.dyhouse.galactics.communication.exception.CommunicationCommandException;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public interface CustomHeader {
    void checkFields() throws CommunicationCommandException;
}
