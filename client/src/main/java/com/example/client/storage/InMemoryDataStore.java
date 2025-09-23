package com.example.client.storage;

import com.example.client.model.Course;
import com.example.client.model.Grade;
import com.example.client.model.Research;
import com.example.client.model.Student;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryDataStore {
	private final Map<String, Student> students = new ConcurrentHashMap<>();
	private final Map<String, Course> courses = new ConcurrentHashMap<>();
	private final Map<String, Research> research = new ConcurrentHashMap<>();
	private final Map<String, Grade> grades = new ConcurrentHashMap<>();

	public void saveStudent(Student student) {
		if (student != null)
			students.put(student.getStudentId(), student);
	}

	public void saveCourse(Course course) {
		if (course != null)
			courses.put(course.getCourseId(), course);
	}

	public void saveResearch(Research researchItem) {
		if (researchItem != null)
			this.research.put(researchItem.getResearchId(), researchItem);
	}

	public void saveGrade(Grade grade) {
		if (grade != null) {
			String key = grade.getStudentId() + "_" + grade.getCourseId();
			grades.put(key, grade);
		}
	}

	public List<Student> getAllStudents() {
		return new ArrayList<>(students.values());
	}

	public List<Course> getAllCourses() {
		return new ArrayList<>(courses.values());
	}

	public List<Research> getAllResearch() {
		return new ArrayList<>(research.values());
	}

	public List<Grade> getAllGrades() {
		return new ArrayList<>(grades.values());
	}
}
