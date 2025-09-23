package com.example.client.service;

import com.example.client.model.Course;
import com.example.client.model.Grade;
import com.example.client.model.Research;
import com.example.client.model.Student;
import com.example.client.storage.InMemoryDataStore;
import com.example.client.storage.InMemoryVectorStore;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class DataLoaderService {
	private static final String XLSX_PATH = "data/university_data.xlsx";

	private final InMemoryDataStore dataStore;
	private final InMemoryVectorStore vectorStore;
	private final TextProcessingService textProcessingService;

	@PostConstruct
	public synchronized void loadData() {
		log.info("Starting data loading process...");

		// Step 1: Attempt to load existing vector store from disk.
		boolean loadedFromDisk = vectorStore.loadFromFile(null);
		if (loadedFromDisk) {
			log.info("Vector store loaded from disk, containing {} embeddings.", vectorStore.size());
		} else {
			log.info("No existing vector store found. A new one will be created.");
		}

		// Step 2: Always process the XLSX file to add new or missing data.
		log.info("Checking for new data in XLSX file: {}", XLSX_PATH);
		int initialVectorCount = vectorStore.size();
		loadDataFromXlsx();
		int newVectors = vectorStore.size() - initialVectorCount;

		// Step 3: Save the potentially updated vector store back to disk if changes were made.
		if (newVectors > 0) {
			log.info("Added {} new vector embeddings from the XLSX file.", newVectors);
			try {
				vectorStore.saveToFile(null);
				log.info("Successfully saved updated vector store to disk with {} total embeddings.",
						vectorStore.size());
			} catch (Exception e) {
				log.error("Failed to save updated vector store: {}", e.getMessage(), e);
			}
		} else {
			log.info("No new data found in XLSX file. Vector store is up to date.");
		}
	}

	private void loadDataFromXlsx() {
		try {
			Resource resource = new ClassPathResource(XLSX_PATH);

			if (!resource.exists()) {
				log.warn("XLSX file not found in classpath at '{}'. Creating sample data if vector store is empty.",
						XLSX_PATH);
				if (vectorStore.size() == 0) {
					createSampleData();
				}
				return;
			}

			try (InputStream inputStream = resource.getInputStream();
				 XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

				loadStudents(workbook.getSheet("Students"));
				loadCourses(workbook.getSheet("Courses"));
				loadResearch(workbook.getSheet("Research"));
				loadGrades(workbook.getSheet("Grades"));

				log.info("Finished processing XLSX file.");
			}
		} catch (Exception e) {
			log.error("Error loading data from XLSX, creating sample data as fallback if needed: {}", e.getMessage(),
					e);
			if (vectorStore.size() == 0) {
				createSampleData();
			}
		}
	}

	private void loadStudents(Sheet sheet) {
		if (sheet == null) {
			log.warn("Students sheet not found");
			return;
		}

		Iterator<Row> rows = sheet.iterator();
		if (rows.hasNext())
			rows.next(); // Skip header

		int count = 0;
		while (rows.hasNext()) {
			Row row = rows.next();
			try {
				String studentId = getCellValue(row, 0);
				if (studentId.isEmpty())
					continue; // Skip rows without an ID

				// Always update the in-memory data store for potential non-vector data changes
				String name = getCellValue(row, 1);
				String email = getCellValue(row, 2);
				String program = getCellValue(row, 3);
				Double gpa = getNumericValue(row, 4);
				Student student = new Student(studentId, name, email, program, gpa);
				dataStore.saveStudent(student);

				// Check if embedding already exists before processing
				if (vectorStore.existsById(studentId)) {
					log.trace("Student with ID {} already exists in vector store. Skipping embedding.", studentId);
					continue;
				}

				List<String> chunks = createComprehensiveStudentChunks(student);
				for (int i = 0; i < chunks.size(); i++) {
					Map<String, Object> chunkMeta = new HashMap<>();
					chunkMeta.put("type", "student");
					chunkMeta.put("id", studentId); // The unique ID for the vector
					chunkMeta.put("name", name);
					chunkMeta.put("email", email);
					chunkMeta.put("program", program);
					chunkMeta.put("gpa", gpa);
					chunkMeta.put("chunkIndex", i);
					chunkMeta.put("totalChunks", chunks.size());
					chunkMeta.put("entityType", "student_profile");

					vectorStore.add(chunks.get(i), chunkMeta);
				}
				count++;

			} catch (Exception e) {
				log.warn("Error processing student row {}: {}", row.getRowNum(), e.getMessage());
			}
		}
		if (count > 0)
			log.info("Added {} new students to the vector store.", count);
	}

	private List<String> createComprehensiveStudentChunks(Student student) {
		List<String> chunks = new ArrayList<>();
		if (student == null) {
			return chunks;
		}
		String studentId = student.getStudentId() != null ? student.getStudentId() : "Unknown";
		String name = student.getName() != null ? student.getName() : "Unknown";
		String email = student.getEmail() != null ? student.getEmail() : "Not provided";
		String program = student.getProgram() != null ? student.getProgram() : "Not specified";
		Double gpa = student.getGpa() != null ? student.getGpa() : 0.0;

		chunks.add(String.format("Student Profile: %s (Student ID: %s) is enrolled in the %s program. "
						+ "Contact email: %s. Current cumulative GPA: %.2f. "
						+ "This student can be identified by ID %s, full name %s, or email address %s.", name, studentId,
				program, email, gpa, studentId, name, email));

		chunks.add(String.format("Student ID %s belongs to %s. Name: %s. Program: %s. Email: %s. GPA: %.2f", studentId,
				name, name, program, email, gpa));

		chunks.add(String.format("%s is a student with ID %s enrolled in %s program. "
				+ "Email address: %s. Academic performance GPA: %.2f", name, studentId, program, email, gpa));

		if (student.getEmail() != null && !student.getEmail().trim().isEmpty()) {
			chunks.add(String.format(
					"Email %s belongs to student %s (Student ID: %s). " + "Enrolled in %s program with GPA %.2f", email,
					name, studentId, program, gpa));
		}

		chunks.add(
				String.format("%s program student: %s (ID: %s). " + "Contact: %s. Academic standing GPA: %.2f", program,
						name, studentId, email, gpa));

		chunks.add(String.format("Academic record: %s (ID: %s) has GPA %.2f in %s program. Email: %s", name, studentId,
				gpa, program, email));
		return chunks;
	}

	private void loadCourses(Sheet sheet) {
		if (sheet == null) {
			log.warn("Courses sheet not found");
			return;
		}

		Iterator<Row> rows = sheet.iterator();
		if (rows.hasNext())
			rows.next(); // Skip header

		int count = 0;
		while (rows.hasNext()) {
			Row row = rows.next();
			try {
				String courseId = getCellValue(row, 0);
				if (courseId.isEmpty())
					continue;

				String title = getCellValue(row, 1);
				String description = getCellValue(row, 2);
				int credits = (int) getNumericValue(row, 3);
				String instructor = getCellValue(row, 4);
				int capacity = (int) getNumericValue(row, 5);
				Course course = new Course(courseId, title, description, credits, instructor, capacity);
				dataStore.saveCourse(course);

				if (vectorStore.existsById(courseId)) {
					log.trace("Course with ID {} already exists in vector store. Skipping embedding.", courseId);
					continue;
				}

				List<String> chunks = createComprehensiveCourseChunks(course);
				for (int i = 0; i < chunks.size(); i++) {
					Map<String, Object> chunkMeta = new HashMap<>();
					chunkMeta.put("type", "course");
					chunkMeta.put("id", courseId);
					chunkMeta.put("title", title);
					chunkMeta.put("description", description);
					chunkMeta.put("instructor", instructor);
					chunkMeta.put("credits", credits);
					chunkMeta.put("capacity", capacity);
					chunkMeta.put("chunkIndex", i);
					chunkMeta.put("totalChunks", chunks.size());
					chunkMeta.put("entityType", "course_info");
					vectorStore.add(chunks.get(i), chunkMeta);
				}
				count++;

			} catch (Exception e) {
				log.warn("Error processing course row {}: {}", row.getRowNum(), e.getMessage());
			}
		}
		if (count > 0)
			log.info("Added {} new courses to the vector store.", count);
	}

	private List<String> createComprehensiveCourseChunks(Course course) {
		List<String> chunks = new ArrayList<>();
		chunks.add(String.format("Course Information: %s (Course ID: %s) is taught by %s. "
						+ "Course description: %s. Academic credit hours: %d. "
						+ "Maximum student capacity: %d. This course can be found by ID %s, "
						+ "title %s, or instructor name %s.", course.getTitle(), course.getCourseId(), course.getInstructor(),
				course.getDescription(), course.getCredits(), course.getCapacity(), course.getCourseId(),
				course.getTitle(), course.getInstructor()));

		chunks.add(String.format("Course ID %s is %s. Instructor: %s. Credits: %d. Capacity: %d. " + "Description: %s",
				course.getCourseId(), course.getTitle(), course.getInstructor(), course.getCredits(),
				course.getCapacity(), course.getDescription()));

		chunks.add(String.format("%s (Course ID: %s) taught by %s. %d credit hours. "
						+ "Class capacity: %d students. Course content: %s", course.getTitle(), course.getCourseId(),
				course.getInstructor(), course.getCredits(), course.getCapacity(), course.getDescription()));

		chunks.add(String.format("Instructor %s teaches %s (Course ID: %s). "
						+ "Course description: %s. Credit value: %d hours. Maximum enrollment: %d", course.getInstructor(),
				course.getTitle(), course.getCourseId(), course.getDescription(), course.getCredits(),
				course.getCapacity()));

		if (course.getDescription() != null && !course.getDescription().trim().isEmpty()) {
			chunks.add(String.format(
					"Course content for %s (ID: %s): %s. " + "Taught by %s for %d credits with capacity %d",
					course.getTitle(), course.getCourseId(), course.getDescription(), course.getInstructor(),
					course.getCredits(), course.getCapacity()));

			if (course.getDescription().length() > 100) {
				List<String> descriptionChunks = textProcessingService.chunkText(
						String.format("Course %s (%s) taught by %s: %s", course.getTitle(), course.getCourseId(),
								course.getInstructor(), course.getDescription()));
				chunks.addAll(descriptionChunks);
			}
		}

		chunks.add(String.format("Academic details: %s (ID: %s) is worth %d credit hours. "
						+ "Class size limit: %d students. Instructor: %s", course.getTitle(), course.getCourseId(),
				course.getCredits(), course.getCapacity(), course.getInstructor()));
		return chunks;
	}

	private void loadResearch(Sheet sheet) {
		if (sheet == null) {
			log.warn("Research sheet not found");
			return;
		}

		Iterator<Row> rows = sheet.iterator();
		if (rows.hasNext())
			rows.next(); // Skip header

		int count = 0;
		while (rows.hasNext()) {
			Row row = rows.next();
			try {
				String researchId = getCellValue(row, 0);
				if (researchId.isEmpty())
					continue;

				String title = getCellValue(row, 1);
				String abstractText = getCellValue(row, 2);
				String authors = getCellValue(row, 3);
				Integer year = (int) getNumericValue(row, 4);
				String keywords = getCellValue(row, 5);
				Research research = new Research(researchId, title, abstractText, authors, year, keywords);
				dataStore.saveResearch(research);

				if (vectorStore.existsById(researchId)) {
					log.trace("Research with ID {} already exists in vector store. Skipping embedding.", researchId);
					continue;
				}

				List<String> chunks = createComprehensiveResearchChunks(research);
				for (int i = 0; i < chunks.size(); i++) {
					Map<String, Object> chunkMeta = new HashMap<>();
					chunkMeta.put("type", "research");
					chunkMeta.put("id", researchId);
					chunkMeta.put("title", title);
					chunkMeta.put("abstract", abstractText);
					chunkMeta.put("authors", authors);
					chunkMeta.put("year", year);
					chunkMeta.put("keywords", keywords);
					chunkMeta.put("chunkIndex", i);
					chunkMeta.put("totalChunks", chunks.size());
					chunkMeta.put("entityType", "research_paper");
					vectorStore.add(chunks.get(i), chunkMeta);
				}
				count++;

			} catch (Exception e) {
				log.warn("Error processing research row {}: {}", row.getRowNum(), e.getMessage());
			}
		}
		if (count > 0)
			log.info("Added {} new research papers to the vector store.", count);
	}

	private List<String> createComprehensiveResearchChunks(Research research) {
		List<String> chunks = new ArrayList<>();
		chunks.add(String.format(
				"Research Paper: %s (Research ID: %s) published in %d. " + "Authors: %s. Keywords: %s. Abstract: %s. "
						+ "This research can be found by ID %s, title %s, authors %s, or keywords %s.",
				research.getTitle(), research.getResearchId(), research.getYear(), research.getAuthors(),
				research.getKeywords(), research.getAbstractText(), research.getResearchId(), research.getTitle(),
				research.getAuthors(), research.getKeywords()));

		chunks.add(
				String.format("Research ID %s: %s. Authors: %s. Publication year: %d. " + "Keywords: %s. Abstract: %s",
						research.getResearchId(), research.getTitle(), research.getAuthors(), research.getYear(),
						research.getKeywords(), research.getAbstractText()));

		chunks.add(String.format("%s (Research ID: %s) by %s (%d). " + "Research keywords: %s. Summary: %s",
				research.getTitle(), research.getResearchId(), research.getAuthors(), research.getYear(),
				research.getKeywords(), research.getAbstractText()));

		chunks.add(String.format("Authors %s published %s (ID: %s) in %d. " + "Keywords: %s. Abstract: %s",
				research.getAuthors(), research.getTitle(), research.getResearchId(), research.getYear(),
				research.getKeywords(), research.getAbstractText()));

		chunks.add(String.format(
				"Research keywords: %s. Paper title: %s (ID: %s). " + "Authors: %s. Year: %d. Abstract: %s",
				research.getKeywords(), research.getTitle(), research.getResearchId(), research.getAuthors(),
				research.getYear(), research.getAbstractText()));

		chunks.add(String.format("%d publication: %s by %s (Research ID: %s). " + "Topics: %s. Abstract: %s",
				research.getYear(), research.getTitle(), research.getAuthors(), research.getResearchId(),
				research.getKeywords(), research.getAbstractText()));

		if (research.getAbstractText() != null && !research.getAbstractText().trim().isEmpty()) {
			chunks.add(String.format("Research abstract for %s (ID: %s) by %s: %s. " + "Published %d. Keywords: %s",
					research.getTitle(), research.getResearchId(), research.getAuthors(), research.getAbstractText(),
					research.getYear(), research.getKeywords()));

			if (research.getAbstractText().length() > 200) {
				List<String> abstractChunks = textProcessingService.chunkText(
						String.format("Research %s (ID: %s) by %s (%d) abstract: %s Keywords: %s", research.getTitle(),
								research.getResearchId(), research.getAuthors(), research.getYear(),
								research.getAbstractText(), research.getKeywords()));
				chunks.addAll(abstractChunks);
			}
		}
		return chunks;
	}

	private void loadGrades(Sheet sheet) {
		if (sheet == null) {
			log.warn("Grades sheet not found");
			return;
		}

		Iterator<Row> rows = sheet.iterator();
		if (rows.hasNext())
			rows.next(); // Skip header

		int count = 0;
		while (rows.hasNext()) {
			Row row = rows.next();
			try {
				String studentId = getCellValue(row, 0);
				String courseId = getCellValue(row, 1);
				if (studentId.isEmpty() || courseId.isEmpty())
					continue;

				// Create a unique, predictable ID for the grade record
				String uniqueGradeId = "grade:" + studentId + ":" + courseId;

				String grade = getCellValue(row, 2);
				Double points = getNumericValue(row, 3);
				Grade gradeObj = new Grade(studentId, courseId, grade, points);
				dataStore.saveGrade(gradeObj);

				if (vectorStore.existsById(uniqueGradeId)) {
					log.trace("Grade for student {} in course {} already exists. Skipping embedding.", studentId,
							courseId);
					continue;
				}

				List<String> chunks = createComprehensiveGradeChunks(gradeObj);
				for (int i = 0; i < chunks.size(); i++) {
					Map<String, Object> chunkMeta = new HashMap<>();
					chunkMeta.put("type", "grade");
					chunkMeta.put("id", uniqueGradeId); // Assign the unique composite ID
					chunkMeta.put("studentId", studentId);
					chunkMeta.put("courseId", courseId);
					chunkMeta.put("grade", grade);
					chunkMeta.put("points", points);
					chunkMeta.put("chunkIndex", i);
					chunkMeta.put("totalChunks", chunks.size());
					chunkMeta.put("entityType", "grade_record");
					vectorStore.add(chunks.get(i), chunkMeta);
				}
				count++;

			} catch (Exception e) {
				log.warn("Error processing grade row {}: {}", row.getRowNum(), e.getMessage());
			}
		}
		if (count > 0)
			log.info("Added {} new grade records to the vector store.", count);
	}

	private List<String> createComprehensiveGradeChunks(Grade grade) {
		List<String> chunks = new ArrayList<>();
		chunks.add(String.format("Grade Record: Student %s received grade %s in course %s. "
						+ "Grade points: %.2f. This grade record links student ID %s with course ID %s "
						+ "showing academic performance %s worth %.2f points.", grade.getStudentId(), grade.getGrade(),
				grade.getCourseId(), grade.getPoints(), grade.getStudentId(), grade.getCourseId(), grade.getGrade(),
				grade.getPoints()));

		chunks.add(String.format(
				"Student %s grades: %s in course %s (%.2f points). " + "Academic performance record for student ID %s",
				grade.getStudentId(), grade.getGrade(), grade.getCourseId(), grade.getPoints(), grade.getStudentId()));

		chunks.add(String.format(
				"Course %s grade: Student %s earned %s (%.2f points). " + "Grade distribution data for course ID %s",
				grade.getCourseId(), grade.getStudentId(), grade.getGrade(), grade.getPoints(), grade.getCourseId()));

		chunks.add(String.format(
				"Grade %s earned by student %s in course %s. " + "Point value: %.2f. Academic achievement record",
				grade.getGrade(), grade.getStudentId(), grade.getCourseId(), grade.getPoints()));

		chunks.add(String.format("Grade points %.2f: Student %s received %s in course %s. "
						+ "Numerical grade value and letter grade correlation", grade.getPoints(), grade.getStudentId(),
				grade.getGrade(), grade.getCourseId()));
		return chunks;
	}

	private String getCellValue(Row row, int index) {
		if (row == null)
			return "";
		Cell cell = row.getCell(index);
		if (cell == null)
			return "";

		try {
			return switch (cell.getCellType()) {
				case STRING -> cell.getStringCellValue().trim();
				case NUMERIC -> {
					double numValue = cell.getNumericCellValue();
					if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
						yield String.valueOf((long) numValue);
					} else {
						yield String.valueOf(numValue);
					}
				}
				case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
				case FORMULA -> {
					try {
						yield String.valueOf(cell.getNumericCellValue());
					} catch (Exception e) {
						yield cell.getCellFormula();
					}
				}
				case BLANK -> "";
				default -> cell.toString().trim();
			};
		} catch (Exception e) {
			log.warn("Error reading cell at row {}, index {}: {}", row.getRowNum(), index, e.getMessage());
			return "";
		}
	}

	private double getNumericValue(Row row, int index) {
		if (row == null)
			return 0.0;
		Cell cell = row.getCell(index);
		if (cell == null)
			return 0.0;

		try {
			return switch (cell.getCellType()) {
				case NUMERIC -> cell.getNumericCellValue();
				case STRING -> {
					String stringValue = cell.getStringCellValue().trim();
					if (stringValue.isEmpty())
						yield 0.0;
					try {
						yield Double.parseDouble(stringValue);
					} catch (NumberFormatException e) {
						log.warn("Cannot parse numeric value from string '{}' at row {}, index {}", stringValue,
								row.getRowNum(), index);
						yield 0.0;
					}
				}
				case FORMULA -> {
					try {
						yield cell.getNumericCellValue();
					} catch (Exception e) {
						log.warn("Cannot evaluate formula at row {}, index {}: {}", row.getRowNum(), index,
								e.getMessage());
						yield 0.0;
					}
				}
				default -> 0.0;
			};
		} catch (Exception e) {
			log.warn("Error reading numeric value at row {}, index {}: {}", row.getRowNum(), index, e.getMessage());
			return 0.0;
		}
	}

	private void createSampleData() {
		log.info("Creating sample data as no XLSX file was found.");
		List<Student> students = Arrays.asList(
				new Student("STU001", "John Doe", "john.doe@university.edu", "Computer Science", 3.75),
				new Student("STU002", "Jane Smith", "jane.smith@university.edu", "Mathematics", 3.85),
				new Student("STU003", "Bob Johnson", "bob.johnson@university.edu", "Physics", 3.60));

		for (Student student : students) {
			dataStore.saveStudent(student);
			if (vectorStore.existsById(student.getStudentId()))
				continue;
			List<String> chunks = createComprehensiveStudentChunks(student);
			for (int i = 0; i < chunks.size(); i++) {
				Map<String, Object> chunkMeta = new HashMap<>();
				chunkMeta.put("type", "student");
				chunkMeta.put("id", student.getStudentId());
				chunkMeta.put("name", student.getName());
				chunkMeta.put("email", student.getEmail());
				chunkMeta.put("program", student.getProgram());
				chunkMeta.put("gpa", student.getGpa());
				chunkMeta.put("chunkIndex", i);
				chunkMeta.put("totalChunks", chunks.size());
				chunkMeta.put("entityType", "student_profile");
				vectorStore.add(chunks.get(i), chunkMeta);
			}
		}

		List<Course> courses = Arrays.asList(
				new Course("CS101", "Introduction to Programming", "Basic programming concepts and fundamentals", 3,
						"Dr. Ada Lovelace", 30),
				new Course("MATH201", "Calculus I", "Differential and integral calculus with applications", 4,
						"Dr. Isaac Newton", 25));

		for (Course course : courses) {
			dataStore.saveCourse(course);
			if (vectorStore.existsById(course.getCourseId()))
				continue;
			List<String> chunks = createComprehensiveCourseChunks(course);
			for (int i = 0; i < chunks.size(); i++) {
				Map<String, Object> chunkMeta = new HashMap<>();
				chunkMeta.put("type", "course");
				chunkMeta.put("id", course.getCourseId());
				chunkMeta.put("title", course.getTitle());
				chunkMeta.put("description", course.getDescription());
				chunkMeta.put("instructor", course.getInstructor());
				chunkMeta.put("credits", course.getCredits());
				chunkMeta.put("capacity", course.getCapacity());
				chunkMeta.put("chunkIndex", i);
				chunkMeta.put("totalChunks", chunks.size());
				chunkMeta.put("entityType", "course_info");
				vectorStore.add(chunks.get(i), chunkMeta);
			}
		}
		log.info("Sample data created with comprehensive chunking.");
	}
}
