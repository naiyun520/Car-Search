package cn.naiblog.carsearch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {
    @GetMapping("/") public String root() { return "redirect:/admin/"; }
    @GetMapping({"/admin", "/admin/"}) public String admin() { return "forward:/admin/index.html"; }
}
