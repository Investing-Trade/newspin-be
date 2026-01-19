package org.gp.newspinbe.global.security;

import lombok.RequiredArgsConstructor;

import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepository.findByEmail(email)
			.map(CustomUserDetails::new)
			.orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
	}
}
