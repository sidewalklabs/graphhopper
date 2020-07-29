package com.graphhopper.stableid;

import com.graphhopper.routing.ev.DefaultEncodedValueFactory;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.UnsignedIntEncodedValue;

public class EncodedValueFactoryWithStableId extends DefaultEncodedValueFactory {
    @Override
    public EncodedValue create(String encodedValueString) {
        if (encodedValueString.startsWith("stable-id-byte-0")) {
            return new UnsignedIntEncodedValue("stable-id-byte-0", 8, false);
        } else if (encodedValueString.startsWith("stable-id-byte-1")) {
            return new UnsignedIntEncodedValue("stable-id-byte-1", 8, false);
        } else if (encodedValueString.startsWith("stable-id-byte-2")) {
            return new UnsignedIntEncodedValue("stable-id-byte-2", 8, false);
        } else if (encodedValueString.startsWith("stable-id-byte-3")) {
            return new UnsignedIntEncodedValue("stable-id-byte-3", 8, false);
        } else if (encodedValueString.startsWith("stable-id-byte-4")) {
            return new UnsignedIntEncodedValue("stable-id-byte-4", 8, false);
        } else if (encodedValueString.startsWith("stable-id-byte-5")) {
            return new UnsignedIntEncodedValue("stable-id-byte-5", 8, false);
        } else if (encodedValueString.startsWith("stable-id-byte-6")) {
            return new UnsignedIntEncodedValue("stable-id-byte-6", 8, false);
        } else if (encodedValueString.startsWith("stable-id-byte-7")) {
            return new UnsignedIntEncodedValue("stable-id-byte-7", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-0")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-0", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-1")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-1", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-2")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-2", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-3")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-3", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-4")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-4", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-5")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-5", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-6")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-6", 8, false);
        } else if (encodedValueString.startsWith("reverse-stable-id-byte-7")) {
            return new UnsignedIntEncodedValue("reverse-stable-id-byte-7", 8, false);
        } else {
            return super.create(encodedValueString);
        }
    }
}
