package com.party.management.service;

import com.party.management.model.Member;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Converts a sign-in file into names and matches them against the member directory. */
public interface SignInRecognitionService {
    RecognitionResult recognize(MultipartFile file, List<Member> members) throws IOException;

    record RecognitionResult(List<String> recognizedNames, List<Member> matchedMembers,
                             List<String> unmatchedNames, List<String> absentNames) {}
}
