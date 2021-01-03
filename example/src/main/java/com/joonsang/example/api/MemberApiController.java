package com.joonsang.example.api;

import com.joonsang.example.domain.Member;
import com.joonsang.example.service.MemberService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    /**
     * 등록 V1: 요청 값으로 Member 엔티티를 직접 받는다.
     *
     * - 엔티티에 프레젠테이션 계층을 위한 로직이 추가된다.
     * - 엔티티가 변경되면 API 스펙이 변한다.
     * - 스펙을 보지 않고는 무슨 필드가 올지 모른다.
     */
    @PostMapping("/api/v1/members")
    public CreateMemberResponse saveMemberV1(@RequestBody @Valid Member member) {
        Long id = memberService.join(member);
        return new CreateMemberResponse(id);
    }

    /**
     * 등록 V2: 요청 값으로 Member Entity 대신에 별도의 DTO를 받는다.
     *
     * - Entity 에 직접 접근하지 않는다.
     * - Api 스펙에 맞는 Validation 가능
     * - 무슨 필드가 들어 올지 명확하다.
     * - Entity 와 APi 스펙을 명확히 분리
     * - Entity 가 변경이 되어도 Api 스펙이 변경 되지 않는다.
     * - Entity 를 파라미터로 받는 것은 지양
     * - Entity 를 외부로 노출하는 것은 지양
     */
    @PostMapping("/api/v2/members")
    public CreateMemberResponse saveMemberV2(@RequestBody @Valid CreateMemberRequest request) {
        Member member = new Member();
        member.setName(request.getName());
        Long id = memberService.join(member);
        return new CreateMemberResponse(id);
    }

    /**
     * 수정 V2
     */
    @PutMapping("/api/v2/members/{id}")
    public UpdateMemberResponse updateMemberV2(@PathVariable("id") Long id, @RequestBody @Valid UpdateMemberRequest request) {
        memberService.update(id, request.getName());
        Member findMember = memberService.findOne(id);
        return new UpdateMemberResponse(findMember.getId(), findMember.getName());
    }

    /**
     * 조회 V1: 요청 값으로 Member 엔티티를 직접 받는다.
     *
     * - Entity 노출
     * - Entity 직접 접근
     * - Api 스펙에 맞는 Validation 불가능
     */
    @GetMapping("/api/v1/members")
    public List<Member> membersV1() {
        return memberService.findMembers();
    }

    /**
     * 조회 V2: 응답 값으로 엔티티가 아닌 별도의 DTO를 반환한다.
     */
    @GetMapping("/api/v2/members")
    public Result membersV2() {
        List<Member> findMembers = memberService.findMembers();
        //엔티티 -> DTO 변환
        List<MemberDto> collect = findMembers.stream()
                .map(m -> new MemberDto(m.getName()))
                .collect(Collectors.toList());
        return new Result(collect);
    }



    /** ***************************************************** **/
    /** ************************* DTO *********************** **/
    /** ***************************************************** **/

    //== 등록
    @Data
    static class CreateMemberRequest {
        private String name;
    }

    @Data
    class CreateMemberResponse {
        private Long id;

        public CreateMemberResponse(Long id) {
            this.id = id;
        }
    }

    //== 수정
    @Data
    static class UpdateMemberRequest {
        private String name;
    }

    @Data
    @AllArgsConstructor
    class UpdateMemberResponse {
        private Long id;
        private String name;
    }

    //== 조회
    @Data
    @AllArgsConstructor
    class Result<T> {
        private T data;
    }
    @Data
    @AllArgsConstructor
    class MemberDto {
        private String name;
    }
}
