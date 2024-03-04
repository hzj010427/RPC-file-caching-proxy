#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>



int main(int argc, char *argv[]) {
    // slowly(sleeping after each reading) a file
    const char *filename = argv[1];
    int fd = open(filename, O_RDONLY);

    if (fd == -1) {
        perror("open");
        exit(1);
    }

    char buffer[10240];

    while (1) {
        ssize_t bytes_read = read(fd, buffer, sizeof(buffer));
        if (bytes_read == -1) {
            printf("read eof\n");
            break;
        }
        if (bytes_read == 0) {
            printf("read eof\n");
            break;
        }
        write(STDOUT_FILENO, buffer, bytes_read);
        // sleep in miliseconds
        usleep(250000);
    }
    
    printf("closing file\n");
    close(fd);
    return 0;
}

