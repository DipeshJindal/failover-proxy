package dev.dpjindal.eagle.controller;

import dev.dpjindal.eagle.iterfc.Interface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Autowired
    private Interface anInterface;

    @GetMapping("/getValue")
    public String getValue(){
        return anInterface.giveMeValue();
    }
}
