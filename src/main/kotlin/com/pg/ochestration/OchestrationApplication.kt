package com.pg.ochestration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class OchestrationApplication

fun main(args: Array<String>) {
    runApplication<OchestrationApplication>(*args)
}
