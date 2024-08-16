package dev.dpjindal.eagle.impl;

import dev.dpjindal.eagle.config.FailoverProxyService;
import dev.dpjindal.eagle.config.FailoverProxyValidate;
import dev.dpjindal.eagle.iterfc.Interface;
import org.springframework.stereotype.Service;

@Service("methodA")
@FailoverProxyService("A_METHOD")
@FailoverProxyValidate("B_METHOD")
public class MethodAImplementation implements Interface {
    @Override
    public String giveMeValue() {
        return "I'm A";
    }
}
