package coco.ide.ideapp.files;

import coco.ide.ideapp.files.requestdto.CreateFileForm;
import coco.ide.ideapp.files.responsedto.ForExecuteDto;
import coco.ide.ideapp.folders.Folder;
import coco.ide.ideapp.folders.FolderRepository;
import coco.ide.ideapp.projects.Project;
import coco.ide.ideapp.projects.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final ProjectRepository projectRepository;

    public ForExecuteDto getFilePath(Long fileId) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일이 없습니다."));
        return new ForExecuteDto(file.getPath(), file.getProject().getLanguage());
    }

    @Transactional
    public boolean createFile(Long projectId, Long folderId, CreateFileForm form) {
        Project findProject = projectRepository.findById(projectId).get();
        Long memberId = findProject.getMember().getMemberId();

        if(isDuplicateName(form.getName(), folderId, projectId)) {
            return false;
        }

        File file = File.builder()
                .name(form.getName())
                .path("")
                .project(findProject)
                .build();

        file.setFolder(folderId != 0 ? folderRepository.findById(folderId).get() : null);

        //filedb에 파일 생성
        String dirPath = folderId != 0 ? "filedb/" + memberId + "/" + projectId + "/" + folderId : "filedb/" + memberId + "/" + projectId;
        java.io.File directory = new java.io.File(dirPath);
        java.io.File createdFile = new java.io.File(directory, form.getName());

        if (!createdFile.exists()) {
            try {
                if (createdFile.createNewFile()) {
                    log.info("파일 생성 성공");
                } else {
                    log.info("파일 생성 실패");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            log.info("파일 이미 존재");
        }

        file.setPath(createdFile.getPath());
        fileRepository.save(file);

        return true;
    }

    @Transactional
    public void deleteFile(Long projectId, Long folderId, Long fileId) {
        if (!fileRepository.existsById(fileId)) {
            throw new IllegalArgumentException("파일 ID" + fileId + "는 존재하지 않습니다.");
        }
        Project findProject = projectRepository.findById(projectId).get();
        Long memberId = findProject.getMember().getMemberId();

        String fileName = fileRepository.findById(fileId).get().getName();
        String dirPath = "filedb/" + memberId + "/" + projectId + "/" + folderId + "/" + fileName;
        java.io.File file = new java.io.File(dirPath);
        file.delete();

        fileRepository.deleteById(fileId);
    }

    @Transactional
    public boolean updateFileName(Long fileId, String newName) {
        File file = fileRepository.findById(fileId).get();

        // 파일이 최상위 폴더에 없는 경우, 폴더에 대한 중복 검사 수행
        if (isDuplicateName(newName, file.getFolder() != null ? file.getFolder().getFolderId() : null, file.getProject().getProjectId())) {
            return false;
        }

        Path filePath = Paths.get(file.getPath()).getParent();
        java.io.File oldFile = new java.io.File(filePath + "/" + file.getName());  // 현재 파일
        java.io.File newFile = new java.io.File(filePath + "/" + newName);  // 새 파일 이름

        if (oldFile.renameTo(newFile)) {
            log.info("File renamed successfully");
        } else {
            log.info("Failed to rename file");
        }


        file.changeName(newName);
        file.setPath(filePath + "/" + newName);
        return true;
    }

    @Transactional
    public boolean updateFilePath(Long projectId, Long folderId, Long fileId) {
        File file = fileRepository.findById(fileId).get();

        if (isDuplicateName(file.getName(), folderId, file.getProject().getProjectId())) {
            return false;
        }
        Project findProject = projectRepository.findById(projectId).get();
        Long memberId = findProject.getMember().getMemberId();

        Folder folder = folderId != 0 ? folderRepository.findById(folderId).get() : null;

        String basicPath = "filedb/" + memberId + "/" + projectId + "/";
        String newPath = folderId == 0 ? basicPath : basicPath + folderId + "/";
        log.info("newPath = {}", newPath + file.getName());


        // 원래 파일 위치
        java.io.File oldFile = new java.io.File(file.getPath());

        // 새 파일 위치
        java.io.File newFile = new java.io.File(newPath + file.getName());

        // 파일 이동
        if (oldFile.renameTo(newFile)) {
            System.out.println("File moved successfully");
        } else {
            System.out.println("Failed to move file");
        }

        file.setFolder(folder);
        return true;
    }

    private boolean isDuplicateName(String newName, Long parentId, Long projectId) {
        List<File> siblings;
        if (parentId == null || parentId == 0) {
            // 최상위 파일의 경우, 프로젝트 내의 다른 최상위 파일들과 비교
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project does not exist"));
            siblings = project.getFiles().stream()
                    .filter(f -> f.getFolder() == null)
                    .collect(Collectors.toList());
        } else {
            // 하위 파일의 경우, 부모 폴더의 자식 파일들과 비교
            Folder parentFolder = folderRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Parent folder does not exist"));
            siblings = parentFolder.getFiles();
        }

        return siblings.stream()
                .anyMatch(f -> f.getName().equals(newName));
    }

    @Transactional
    public void updateFileContent(Long fileId, String newContent) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일이 존재하지 않습니다."));

        String codePath = file.getPath();
        java.io.File codeFile = new java.io.File(codePath);

        try (FileWriter writer = new FileWriter(codeFile, false)) { // false to overwrite.
            writer.write(newContent); // 새로운 코드로 파일을 덮어씁니다.
            log.info("new Content = {}", newContent);
        } catch (IOException e) {
            throw new RuntimeException("파일 쓰기 중 오류가 발생했습니다.", e);
        }
    }

    public String getFileContent(Long fileId) {
        String filePath = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 파일")).getPath();

        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    public Long getMemberId(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 프로젝트")).getMember().getMemberId();
    }
}
