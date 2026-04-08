package com.marketPlace.MarketPlace.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class testContoller {

    @GetMapping("/hello")
    public String hello(){
        return "Active and live";
    }

}
