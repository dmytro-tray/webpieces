package com.webpieces.http2parser.api.dto;

import com.webpieces.http2parser.api.Padding;
import com.webpieces.http2parser.api.PaddingFactory;
import org.webpieces.data.api.DataWrapper;

public class Http2Data extends Http2Frame {

    public Http2FrameType getFrameType() {
        return Http2FrameType.DATA;
    }

    /* flags */
    private boolean endStream = false; /* 0x1 */
    //private boolean padded = false;    /* 0x8 */

    public boolean isEndStream() {
        return endStream;
    }

    public void setEndStream(boolean endStream) {
        this.endStream = endStream;
    }

    /* payload */
    private DataWrapper data = dataGen.emptyWrapper();
    private Padding padding = PaddingFactory.createPadding();

    public Padding getPadding() {
        return padding;
    }

    public DataWrapper getData() {
        return data;
    }

    public void setData(DataWrapper data) {
        this.data = data;
    }

    public void setPadding(byte[] padding) {
        this.padding.setPadding(padding);
    }
}
