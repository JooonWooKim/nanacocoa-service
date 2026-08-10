package com.nanacocoa.server.common.userdetails;

import com.nanacocoa.server.common.exception.ErrorCode;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

	private final MemberRepository memberRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		Member member = memberRepository.findByEmail(email).orElseThrow(
				() -> new NanacocoaException(ErrorCode.NOT_FOUND_EMAIL));
		return new UserDetailsImpl(member, member.getEmail());
	}
}
