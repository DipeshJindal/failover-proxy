package dev.dpjindal.eagle.impl;

import dev.dpjindal.eagle.config.FailoverProxyService;
import dev.dpjindal.eagle.config.FailoverProxyValidate;
import dev.dpjindal.eagle.iterfc.Interface;
import org.springframework.stereotype.Service;

@Service("methodB")
@FailoverProxyService("B_METHOD")
@FailoverProxyValidate("C_METHOD")
public class MethodBImplementation implements Interface {
    @Override
    public String giveMeValue() {
        return "I'm B";
    }
}
