package com.reactividadMongo.example.repository;

import com.reactividadMongo.example.model.Student;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface StudentReactiveRepository extends ReactiveCrudRepository<Student,String> {
}
