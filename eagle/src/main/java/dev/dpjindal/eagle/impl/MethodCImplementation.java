package dev.dpjindal.eagle.impl;

import dev.dpjindal.eagle.config.FailoverProxy;
import dev.dpjindal.eagle.config.FailoverProxyService;
import dev.dpjindal.eagle.config.FailoverProxyValidate;
import dev.dpjindal.eagle.iterfc.Interface;
import org.springframework.stereotype.Service;

@Service("methodC")
@FailoverProxyService("C_METHOD")
@FailoverProxyValidate("A_METHOD")
public class MethodCImplementation implements Interface {
    @Override
    public String giveMeValue() {
        return "I'm C";
    }
}
