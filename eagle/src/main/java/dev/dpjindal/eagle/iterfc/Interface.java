package dev.dpjindal.eagle.iterfc;

import dev.dpjindal.eagle.config.FailoverProxy;

@FailoverProxy("Interface")
public interface Interface {
    String giveMeValue();
}
